package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.CollectorProfileGuard;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.SubmitMutation;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskPoolService {
	private final TaskStore tasks;
	private final TaskVersionStore versions;
	private final TaskGrantStore grants;
	private final TaskItemStore items;
	private final Clock clock;
	private final CollectorProfileGuard profileGuard;

	@org.springframework.beans.factory.annotation.Autowired
	public TaskPoolService(
		TaskStore tasks,
		TaskVersionStore versions,
		TaskGrantStore grants,
		TaskItemStore items,
		Clock clock,
		MiniProgramUserStore users
	) {
		this.tasks = tasks;
		this.versions = versions;
		this.grants = grants;
		this.items = items;
		this.clock = clock;
		this.profileGuard = new CollectorProfileGuard(users);
	}

	public TaskPoolService(TaskStore tasks, TaskVersionStore versions, TaskGrantStore grants, TaskItemStore items, Clock clock) {
		this.tasks = tasks; this.versions = versions; this.grants = grants; this.items = items; this.clock = clock; this.profileGuard = null;
	}

	public TaskItem start(String taskId, PlatformPrincipal actor) {
		requireCollector(actor);
		requireProfile(actor);
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != TaskLifecycle.RUNNING) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_NOT_RUNNING", "只有进行中的任务可以领取");
		}
		if (grants.findActive(taskId, actor.userId()).isEmpty()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TASK_GRANT_REQUIRED", "没有该任务的有效授权");
		}
		ClaimMutation mutation = new ClaimMutation(
			taskId,
			actor.userId(),
			actor.username() == null ? actor.name() : actor.username(),
			UUID.randomUUID().toString(),
			Instant.now(clock)
		);
		return items.claimAvailable(mutation).orElseThrow(() ->
			new ApiException(HttpStatus.NOT_FOUND, "NO_AVAILABLE_ITEM", "当前没有可领取的数据")
		);
	}

	public TaskItemActionResult submit(String itemId, SubmitTaskItemCommand command, PlatformPrincipal actor) {
		requireCollector(actor);
		requireProfile(actor);
		TaskItem item = requireItem(itemId);
		TaskItemActionResult replay = replay(item, command.operationId(), actor.userId());
		if (replay != null) return replay;
		if ((item.getStatus() != TaskItemStatus.RECORDING_PENDING
			&& item.getStatus() != TaskItemStatus.REWORK_PENDING
			&& item.getStatus() != TaskItemStatus.SUBMITTED)
			|| !actor.userId().equals(item.getCollectorId())
			|| !safeEquals(command.assignmentId(), item.getAssignmentId())
			|| command.expectedRevision() != item.getRevision()) {
			throw stale();
		}
		TaskVersion version = requireVersion(item.getTaskVersionId());
		String text = trimToNull(command.text());
		validateSubmission(version, text, command.audio());
		TaskItemStatus target = version.isHumanReviewEnabled()
			? TaskItemStatus.SUBMITTED : TaskItemStatus.COMPLETED;
		SubmitMutation mutation = new SubmitMutation(
			itemId,
			actor.userId(),
			actor.username() == null ? actor.name() : actor.username(),
			command.assignmentId(),
			command.expectedRevision(),
			requiredOperationId(command.operationId()),
			new TaskItemResult(command.audio(), text),
			target,
			Instant.now(clock)
		);
		return finishOrReplay(items.submitIfCurrent(mutation), itemId, command.operationId(), actor.userId());
	}

	public TaskItemActionResult reject(
		String itemId,
		String operationId,
		long expectedRevision,
		String reason,
		PlatformPrincipal actor
	) {
		if (actor == null || (actor.role() != UserRole.REVIEWER && actor.role() != UserRole.ADMIN)) {
			throw forbidden();
		}
		TaskItem item = requireItem(itemId);
		TaskItemActionResult replay = replay(item, operationId, actor.userId());
		if (replay != null) return replay;
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != expectedRevision
			|| item.getReviewerId() == null || item.getReviewAssignmentId() == null
			|| actor.role() == UserRole.REVIEWER && !actor.userId().equals(item.getReviewerId())) {
			throw stale();
		}
		String normalizedReason = trimToNull(reason);
		TaskVersion version = requireVersion(item.getTaskVersionId());
		if (normalizedReason == null || !version.getRejectionReasons().contains(normalizedReason)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REJECTION_REASON", "请选择任务配置的驳回原因");
		}
		RejectMutation mutation = new RejectMutation(
			itemId,
			actor.userId(),
			actor.username() == null ? actor.name() : actor.username(),
			expectedRevision,
			requiredOperationId(operationId),
			normalizedReason,
			latestSubmissionOperationId(item),
			item.getAssignmentId(),
			item.getCurrentResult(),
			Instant.now(clock)
		);
		return finishOrReplay(items.rejectIfCurrent(mutation), itemId, operationId, actor.userId());
	}

	public TaskItemActionResult release(
		String itemId,
		String operationId,
		long expectedRevision,
		PlatformPrincipal actor
	) {
		if (actor == null || (actor.role() != UserRole.COLLECTOR && actor.role() != UserRole.ADMIN)) {
			throw forbidden();
		}
		if (actor.role() == UserRole.COLLECTOR) requireProfile(actor);
		TaskItem item = requireItem(itemId);
		TaskItemActionResult replay = replay(item, operationId, actor.userId());
		if (replay != null) return replay;
		if (item.getRevision() != expectedRevision
			|| item.getStatus() == TaskItemStatus.AVAILABLE
			|| item.getStatus() == TaskItemStatus.DISCARDED) {
			throw stale();
		}
		if (actor.role() == UserRole.COLLECTOR
			&& ((item.getStatus() != TaskItemStatus.RECORDING_PENDING && item.getStatus() != TaskItemStatus.REWORK_PENDING)
				|| !actor.userId().equals(item.getCollectorId()))) {
			throw forbidden();
		}
		ReleaseMutation mutation = new ReleaseMutation(
			itemId,
			actor.userId(),
			actor.username() == null ? actor.name() : actor.username(),
			actor.role() == UserRole.ADMIN,
			expectedRevision,
			requiredOperationId(operationId),
			Instant.now(clock)
		);
		return finishOrReplay(items.releaseIfCurrent(mutation), itemId, operationId, actor.userId());
	}

	private TaskItemActionResult finishOrReplay(
		Optional<TaskItem> updated,
		String itemId,
		String operationId,
		String actorUserId
	) {
		if (updated.isPresent()) return TaskItemActionResult.from(updated.get());
		TaskItem current = requireItem(itemId);
		TaskItemActionResult replay = replay(current, operationId, actorUserId);
		if (replay != null) return replay;
		throw stale();
	}

	private TaskItemActionResult replay(TaskItem item, String operationId, String actorUserId) {
		if (operationId == null || actorUserId == null || item.getOperations() == null) return null;
		return item.getOperations().stream()
			.filter((operation) -> operationId.equals(operation.getOperationId())
				&& actorUserId.equals(operation.getActorUserId()))
			.findFirst()
			.map((operation) -> TaskItemActionResult.replay(item.getId(), operation))
			.orElse(null);
	}

	private void validateSubmission(TaskVersion version, String text, SubmittedRecording audio) {
		if (audio == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_REQUIRED", "录音任务必须提交录音");
		}
		if (version.getResultType() == TaskResultType.TEXT) {
			if (text == null || text.isBlank()) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_REQUIRED", "文本成果任务必须同时提交文本");
			}
		} else if (text != null && !text.isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_NOT_ALLOWED", "该任务只允许提交录音");
		}
		if (audio.format() != version.getRecordingFormat()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO_FORMAT", "录音格式不符合任务配置");
		}
		if (audio.channels() != 1 || audio.channels() != version.getChannels()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO_CHANNELS", "录音必须为单声道");
		}
		if (!version.getSampleRates().contains(audio.sampleRate())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO_SAMPLE_RATE", "录音采样率不符合任务配置");
		}
		if (audio.durationMillis() < version.getMinDurationMillis()
			|| audio.durationMillis() > version.getMaxDurationMillis()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO_DURATION", "录音时长不符合任务配置");
		}
	}

	private void requireCollector(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.COLLECTOR) throw forbidden();
	}
	private void requireProfile(PlatformPrincipal actor) { if (profileGuard != null) profileGuard.requireComplete(actor); }

	private TaskRecord requireTask(String taskId) {
		return tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
	}

	private TaskVersion requireVersion(String versionId) {
		return versions.findById(versionId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_VERSION_NOT_FOUND", "任务版本不存在"));
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}

	private String requiredOperationId(String operationId) {
		if (operationId == null || operationId.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		}
		return operationId.trim();
	}

	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String latestSubmissionOperationId(TaskItem item) {
		if (item.getSubmissions() == null || item.getSubmissions().isEmpty()) return null;
		return item.getSubmissions().get(item.getSubmissions().size() - 1).getOperationId();
	}
	private boolean safeEquals(String left, String right) { return left != null && left.equals(right); }
	private ApiException stale() { return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "条目状态或修订号已变化"); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作"); }
}

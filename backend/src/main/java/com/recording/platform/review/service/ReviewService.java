package com.recording.platform.review.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.model.CurrentRejection;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.task.store.AdminReviewDecisionMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskVersionStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReviewService {
	private final TaskItemStore items;
	private final TaskVersionStore versions;
	private final Clock clock;
	private final IdentityDirectory users;
	private final TaskStore tasks;

	public ReviewService(TaskItemStore items, TaskVersionStore versions, Clock clock) {
		this(items, versions, null, null, clock);
	}

	public ReviewService(TaskItemStore items, TaskVersionStore versions, IdentityDirectory users, Clock clock) {
		this(items, versions, users, null, clock);
	}

	@Autowired
	public ReviewService(TaskItemStore items, TaskVersionStore versions, IdentityDirectory users, TaskStore tasks, Clock clock) {
		this.items = items;
		this.versions = versions;
		this.users = users;
		this.tasks = tasks;
		this.clock = clock;
	}

	public TaskItem claim(String operationId, PlatformPrincipal actor) {
		throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_ID_REQUIRED", "请先选择审核任务");
	}

	public TaskItem claim(String taskId, String operationId, PlatformPrincipal actor) {
		requireReviewer(actor);
		return claimOptional(taskId, requiredOperationId(operationId), actor).orElseThrow(() ->
			new ApiException(HttpStatus.NOT_FOUND, "NO_REVIEW_ITEM", "当前没有可领取的待审核数据")
		);
	}

	public List<ReviewTaskSummary> tasks(PlatformPrincipal actor) {
		requireReviewAccess(actor);
		if (tasks == null) return List.of();
		return tasks.findAll(Pageable.unpaged()).getContent().stream()
			.map(task -> new ReviewTaskSummary(task.getId(), task.getTaskCode(), task.getName(),
				items.countReviewPendingByTaskId(task.getId())))
			.filter(summary -> summary.pendingCount() > 0)
			.toList();
	}

	public Page<ReviewPoolItemView> pool(String taskId, Pageable pageable, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		Page<TaskItem> pool = items.findReviewPoolByTaskId(
			taskId, actor.role() == UserRole.ADMIN, actor.role() == UserRole.REVIEWER ? actor.userId() : null, pageable
		);
		Map<String, IdentityUser> collectors = users == null ? Map.of() : users.findAllByIdIn(
			pool.getContent().stream().map(TaskItem::getCollectorId).filter(java.util.Objects::nonNull).distinct().toList()
		).stream().collect(Collectors.toMap(IdentityUser::id, Function.identity()));
		return pool.map(item -> ReviewPoolItemView.from(item, collectors.get(item.getCollectorId())));
	}

	public Page<TaskItem> pool(Pageable pageable, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		return actor.role() == UserRole.ADMIN
			? items.findAllReviewPending(pageable)
			: items.findReviewPool(pageable);
	}

	public List<TaskItem> claimBatch(int count, String operationId, PlatformPrincipal actor) {
		throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_ID_REQUIRED", "请先选择审核任务");
	}

	public List<TaskItem> claimBatch(String taskId, int count, String operationId, PlatformPrincipal actor) {
		requireReviewer(actor);
		if (count < 1 || count > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量领取数量必须为 1 到 100");
		}
		String batchOperation = requiredOperationId(operationId);
		List<TaskItem> claimed = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			var item = claimOptional(taskId, batchOperation + ":" + index, actor);
			if (item.isEmpty()) break;
			claimed.add(item.get());
		}
		if (claimed.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NO_REVIEW_ITEM", "当前没有可领取的待审核数据");
		}
		return claimed;
	}

	public TaskItem assign(
		String itemId, String reviewerId, String operationId, long expectedRevision, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		if (users == null) throw new IllegalStateException("user store required");
		IdentityUser reviewer = users.findById(reviewerId).orElse(null);
		if (reviewer == null || reviewer.role() != UserRole.REVIEWER || reviewer.status() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REVIEWER", "只能分配给启用状态的审核员");
		}
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getReviewerId() != null
			|| item.getRevision() != expectedRevision) throw stale();
		ReviewAssignMutation mutation = new ReviewAssignMutation(
			itemId, reviewerId, reviewer.name(), actor.userId(), actorName(actor), UUID.randomUUID().toString(),
			expectedRevision, requiredOperationId(operationId), Instant.now(clock)
		);
		return items.assignReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	public List<BatchReviewResult> batchApprove(
		String operationId, List<BatchReviewCommand> commands, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		String batchId = requiredOperationId(operationId);
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchReviewResult> results = new ArrayList<>();
		for (int index = 0; index < commands.size(); index++) {
			BatchReviewCommand command = commands.get(index);
			try {
				TaskItem item = requireItem(command.itemId());
				if (item.getStatus() != TaskItemStatus.REVIEW_PENDING
					|| item.getRevision() != command.expectedRevision()) throw stale();
				TaskItemResult current = item.getCurrentResult();
				TaskItemResult result = reviewResult(requireVersion(item.getTaskVersionId()), current, command.text());
				AdminReviewApproveMutation mutation = new AdminReviewApproveMutation(
					item.getId(), actor.userId(), actorName(actor), command.expectedRevision(),
					batchId + ":" + index, result, latestSubmissionOperationId(item), Instant.now(clock)
				);
				TaskItem updated = items.adminApproveReviewIfCurrent(mutation).orElseThrow(this::stale);
				results.add(BatchReviewResult.success(item.getId(), updated.getRevision()));
			} catch (ApiException exception) {
				results.add(BatchReviewResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			}
		}
		return results;
	}

	private java.util.Optional<TaskItem> claimOptional(String taskId, String operationId, PlatformPrincipal actor) {
		String assignmentId = UUID.randomUUID().toString();
		ReviewClaimMutation mutation = new ReviewClaimMutation(
			taskId, actor.userId(), actorName(actor), assignmentId, operationId, Instant.now(clock)
		);
		return items.claimReview(mutation);
	}

	public TaskItem release(
		String itemId,
		String operationId,
		long expectedRevision,
		PlatformPrincipal actor
	) {
		requireReviewer(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING
			|| item.getRevision() != expectedRevision
			|| !actor.userId().equals(item.getReviewerId())
			|| item.getReviewAssignmentId() == null) {
			throw stale();
		}
		ReviewReleaseMutation mutation = new ReviewReleaseMutation(
			itemId, actor.userId(), actorName(actor), item.getReviewAssignmentId(), expectedRevision,
			requiredOperationId(operationId), Instant.now(clock)
		);
		return items.releaseReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	public TaskItem approve(
		String itemId, String operationId, long expectedRevision, String text, PlatformPrincipal actor
	) {
		TaskItem item = requireDecisionItem(itemId, expectedRevision, actor);
		TaskItemResult current = item.getCurrentResult();
		TaskItemResult result = reviewResult(requireVersion(item.getTaskVersionId()), current, text);
		return decide(item, operationId, actor, TaskItemStatus.COMPLETED, result, "审核通过", null);
	}

	private TaskItemResult reviewResult(TaskVersion version, TaskItemResult current, String requestedText) {
		String normalizedText = trimToNull(requestedText);
		if (version.getResultType() == TaskResultType.TEXT) {
			String finalText = normalizedText == null && current != null ? trimToNull(current.text()) : normalizedText;
			if (finalText == null) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_REQUIRED", "文本成果不能为空");
			}
			if (current == null || current.audio() == null) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_REQUIRED", "录音成果不能为空");
			}
			return new TaskItemResult(current.audio(), finalText);
		}
		if (version.getResultType() == TaskResultType.AUDIO) {
			if (normalizedText != null) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_NOT_ALLOWED", "音频任务不允许提交文字成果");
			}
			if (current == null || current.audio() == null) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_REQUIRED", "音频成果不能为空");
			}
			return new TaskItemResult(current.audio(), null);
		}
		throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RESULT_TYPE_REQUIRED", "任务未配置最终成果类型");
	}

	public TaskItem reject(
		String itemId, String operationId, long expectedRevision,
		List<String> reasons, String note, PlatformPrincipal actor
	) {
		TaskItem item = requireDecisionItem(itemId, expectedRevision, actor);
		TaskVersion version = requireVersion(item.getTaskVersionId());
		List<String> normalizedReasons = reasons == null ? List.of() : reasons.stream()
			.map(this::trimToNull).filter(java.util.Objects::nonNull).distinct().toList();
		for (String reason : normalizedReasons) {
			if (!version.getRejectionReasons().contains(reason)) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REJECTION_REASON", "包含任务未配置的驳回原因");
			}
		}
		String normalizedNote = trimToNull(note);
		if (normalizedReasons.isEmpty() && normalizedNote == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REJECTION_REASON_REQUIRED", "请选择驳回原因或填写补充说明");
		}
		String conclusion = String.join("、", normalizedReasons);
		if (normalizedNote != null) conclusion += (conclusion.isEmpty() ? "" : "；") + normalizedNote;
		CurrentRejection rejection = new CurrentRejection(
			normalizedReasons, normalizedNote, Instant.now(clock), actor.userId(), actorName(actor)
		);
		return decide(item, operationId, actor, TaskItemStatus.REWORK_PENDING, item.getCurrentResult(), conclusion, rejection);
	}

	private TaskItem decide(
		TaskItem item, String operationId, PlatformPrincipal actor, TaskItemStatus target,
		TaskItemResult result, String conclusion, CurrentRejection currentRejection
	) {
		if (actor.role() == UserRole.ADMIN) {
			AdminReviewDecisionMutation mutation = new AdminReviewDecisionMutation(
				item.getId(), actor.userId(), actorName(actor), item.getRevision(),
				requiredOperationId(operationId), target, result, conclusion,
				currentRejection, latestSubmissionOperationId(item), Instant.now(clock)
			);
			return items.adminDecideReviewIfCurrent(mutation).orElseThrow(this::stale);
		}
		ReviewDecisionMutation mutation = new ReviewDecisionMutation(
			item.getId(), actor.userId(), actorName(actor), item.getReviewAssignmentId(), item.getRevision(),
			requiredOperationId(operationId), target, result, conclusion, currentRejection,
			latestSubmissionOperationId(item), Instant.now(clock)
		);
		return items.decideReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	private TaskItem requireDecisionItem(String itemId, long expectedRevision, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != expectedRevision) {
			throw stale();
		}
		if (actor.role() == UserRole.REVIEWER
			&& (!actor.userId().equals(item.getReviewerId()) || item.getReviewAssignmentId() == null)) {
			throw stale();
		}
		return item;
	}

	private TaskItem requireAssigned(String itemId, long expectedRevision, PlatformPrincipal actor) {
		requireReviewer(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != expectedRevision
			|| !actor.userId().equals(item.getReviewerId()) || item.getReviewAssignmentId() == null) throw stale();
		return item;
	}

	private TaskVersion requireVersion(String versionId) {
		TaskVersion version = versions.findById(versionId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_VERSION_NOT_FOUND", "任务版本不存在"));
		if (!version.isHumanReviewEnabled()) throw stale();
		return version;
	}

	private String latestSubmissionOperationId(TaskItem item) {
		if (item.getSubmissions() == null || item.getSubmissions().isEmpty()) return null;
		return item.getSubmissions().get(item.getSubmissions().size() - 1).getOperationId();
	}

	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}

	private void requireReviewer(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.REVIEWER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private void requireReviewAccess(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.REVIEWER && actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private String requiredOperationId(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		}
		return value.trim();
	}

	private String actorName(PlatformPrincipal actor) {
		return actor.username() == null ? actor.name() : actor.username();
	}

	private ApiException stale() {
		return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "条目状态或修订号已变化");
	}
}

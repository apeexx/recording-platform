package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.ReferenceMediaUrlValidator;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemCreationService {
	private final TaskStore tasks;
	private final TaskItemStore items;
	private final ReferenceMediaUrlValidator referenceUrls;
	private final Clock clock;

	public TaskItemCreationService(
		TaskStore tasks,
		TaskItemStore items,
		ReferenceMediaUrlValidator referenceUrls,
		Clock clock
	) {
		this.tasks = tasks;
		this.items = items;
		this.referenceUrls = referenceUrls;
		this.clock = clock;
	}

	public TaskItem add(
		String taskId,
		AddTaskItemCommand command,
		String operationId,
		PlatformPrincipal actor
	) {
		return add(taskId, command, operationId, actor, false);
	}

	public TaskItem addImported(
		String taskId,
		AddTaskItemCommand command,
		String operationId,
		PlatformPrincipal actor
	) {
		return add(taskId, command, operationId, actor, true);
	}

	private TaskItem add(
		String taskId,
		AddTaskItemCommand command,
		String operationId,
		PlatformPrincipal actor,
		boolean ignoreDisabledReferences
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) throw forbidden();
		String normalizedOperationId = requiredOperationId(operationId);
		Optional<TaskItem> replay = items.findByTaskIdAndCreationOperationId(taskId, normalizedOperationId);
		if (replay.isPresent()) return replay.get();
		TaskRecord task = tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (task.getLifecycle() == TaskLifecycle.ENDED) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", "已结束任务不能添加数据");
		}
		TaskConfiguration configuration = task.getConfiguration();
		String text = trimToNull(command.referenceText());
		String audioUrl = trimToNull(command.referenceAudioUrl());
		String videoUrl = trimToNull(command.referenceVideoUrl());
		if (ignoreDisabledReferences) {
			text = enabled(configuration, ReferenceType.TEXT) ? text : null;
			audioUrl = enabled(configuration, ReferenceType.AUDIO) ? audioUrl : null;
			videoUrl = enabled(configuration, ReferenceType.VIDEO) ? videoUrl : null;
		}
		if (text == null && audioUrl == null && videoUrl == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_REFERENCE_REQUIRED", "每条数据至少需要一种参考内容");
		}
		if (!ignoreDisabledReferences) validateEnabledReferences(configuration, text, audioUrl, videoUrl);
		audioUrl = referenceUrls.validateNullable(audioUrl);
		videoUrl = referenceUrls.validateNullable(videoUrl);
		long sequence = tasks.nextItemSequence(taskId);
		if (sequence < 1) throw new ApiException(HttpStatus.CONFLICT, "TASK_STATE_CHANGED", "任务状态已变化");
		if (sequence > 1_000_000) {
			throw new ApiException(HttpStatus.CONFLICT, "ITEM_CODE_EXHAUSTED", "该任务的数据条目已达到 100 万上限");
		}
		String itemCode = task.getTaskCode() + "-" + String.format(Locale.ROOT, "%07d", sequence);
		TaskItem item = new TaskItem();
		item.setId(UUID.randomUUID().toString());
		item.setTaskId(taskId);
		item.setSequence(sequence);
		item.setItemCode(itemCode);
		item.setCreationOperationId(normalizedOperationId);
		item.setReferenceText(text);
		item.setReferenceAudioUrl(audioUrl);
		item.setReferenceVideoUrl(videoUrl);
		item.setStatus(TaskItemStatus.AVAILABLE);
		Instant now = Instant.now(clock);
		item.setCreatedAt(now);
		item.setUpdatedAt(now);
		try {
			item.getOperations().add(OperationHistory.creation(
				normalizedOperationId,
				actor.userId(),
				actor.username() == null ? actor.name() : actor.username(),
				now,
				item
			));
			return items.save(item);
		} catch (DuplicateKeyException exception) {
			return items.findByTaskIdAndCreationOperationId(taskId, normalizedOperationId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ITEM_CONFLICT", "任务条目已存在"));
		}
	}

	private boolean enabled(TaskConfiguration configuration, ReferenceType type) {
		if (configuration == null) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		}
		return configuration.getReferenceTypes().contains(type);
	}

	private void validateEnabledReferences(TaskConfiguration configuration, String text, String audio, String video) {
		if (configuration == null) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		}
		if (text != null && !configuration.getReferenceTypes().contains(ReferenceType.TEXT)
			|| audio != null && !configuration.getReferenceTypes().contains(ReferenceType.AUDIO)
			|| video != null && !configuration.getReferenceTypes().contains(ReferenceType.VIDEO)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_TYPE_NOT_ENABLED", "参考内容类型未在任务中启用");
		}
	}

	private String requiredOperationId(String operationId) {
		if (operationId == null || operationId.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "Idempotency-Key 不能为空");
		}
		return operationId.trim();
	}

	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作"); }
}

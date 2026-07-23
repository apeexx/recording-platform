package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
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
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskItemCreationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(TaskItemCreationService.class);
	private final TaskStore tasks;
	private final TaskItemStore items;
	private final SafeRemoteMediaDownloader downloader;
	private final MediaAssetStore assets;
	private final Clock clock;

	public TaskItemCreationService(
		TaskStore tasks,
		TaskItemStore items,
		SafeRemoteMediaDownloader downloader,
		MediaAssetStore assets,
		Clock clock
	) {
		this.tasks = tasks;
		this.items = items;
		this.downloader = downloader;
		this.assets = assets;
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
		if (task.getLifecycle() != TaskLifecycle.RUNNING && task.getLifecycle() != TaskLifecycle.PAUSED) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_VERSION_NOT_PUBLISHED", "任务发布后才能添加数据");
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
		List<MediaAsset> downloaded = new ArrayList<>();
		try {
			if (audioUrl != null) {
				MediaAsset audio = downloader.download(uri(audioUrl), RemoteMediaType.AUDIO, taskId, itemCode);
				downloaded.add(audio);
				audio.setItemId(item.getId());
				assets.save(audio);
				item.setReferenceAudioMediaId(audio.getId());
			}
			if (videoUrl != null) {
				MediaAsset video = downloader.download(uri(videoUrl), RemoteMediaType.VIDEO, taskId, itemCode);
				downloaded.add(video);
				video.setItemId(item.getId());
				assets.save(video);
				item.setReferenceVideoMediaId(video.getId());
			}
			item.getOperations().add(OperationHistory.creation(
				normalizedOperationId,
				actor.userId(),
				actor.username() == null ? actor.name() : actor.username(),
				now,
				item
			));
			return items.save(item);
		} catch (DuplicateKeyException exception) {
			cleanup(downloaded, exception);
			return items.findByTaskIdAndCreationOperationId(taskId, normalizedOperationId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ITEM_CONFLICT", "任务条目已存在"));
		} catch (RuntimeException exception) {
			cleanup(downloaded, exception);
			throw exception;
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

	private URI uri(String value) {
		try {
			return URI.create(value);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REMOTE_URL_INVALID", "远程媒体 URL 不合法");
		}
	}

	private void cleanup(List<MediaAsset> downloaded, RuntimeException original) {
		List<RuntimeException> failures = new ArrayList<>();
		for (MediaAsset asset : downloaded) {
			try {
				downloader.delete(asset);
			} catch (RuntimeException cleanupFailure) {
				failures.add(cleanupFailure);
			}
		}
		if (!failures.isEmpty()) {
			LOGGER.error(
				"Reference media cleanup failed after task item creation failure; targets={}",
				downloaded.size(),
				failures.get(0)
			);
			ApiException controlled = new ApiException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"REFERENCE_MEDIA_CLEANUP_FAILED",
				"任务条目创建失败，参考媒体清理需要人工检查"
			);
			controlled.addSuppressed(original);
			failures.forEach(controlled::addSuppressed);
			throw controlled;
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

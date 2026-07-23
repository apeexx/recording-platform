package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaCleanupService;
import com.recording.platform.media.ReferenceMediaUrlValidator;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.UpdateTaskItemReferencesMutation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemReferenceAdministrationService {
	private final TaskItemStore items;
	private final TaskStore tasks;
	private final ReferenceMediaUrlValidator referenceUrls;
	private final MediaAssetStore assets;
	private final MediaCleanupService cleanup;
	private final Clock clock;

	public TaskItemReferenceAdministrationService(
		TaskItemStore items, TaskStore tasks, ReferenceMediaUrlValidator referenceUrls,
		MediaAssetStore assets, MediaCleanupService cleanup, Clock clock
	) {
		this.items = items;
		this.tasks = tasks;
		this.referenceUrls = referenceUrls;
		this.assets = assets;
		this.cleanup = cleanup;
		this.clock = clock;
	}

	public TaskItem update(
		String itemId, UpdateTaskItemReferencesCommand command, String operationId, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		TaskItem current = requireAvailable(itemId, command.expectedRevision());
		TaskRecord task = tasks.findById(current.getTaskId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		String text = trim(command.referenceText());
		String audioUrl = trim(command.referenceAudioUrl());
		String videoUrl = trim(command.referenceVideoUrl());
		validate(task.getConfiguration(), text, audioUrl, videoUrl);
		audioUrl = referenceUrls.validateNullable(audioUrl);
		videoUrl = referenceUrls.validateNullable(videoUrl);
		String audioId = changed(current.getReferenceAudioUrl(), audioUrl)
			? null : current.getReferenceAudioMediaId();
		String videoId = changed(current.getReferenceVideoUrl(), videoUrl)
			? null : current.getReferenceVideoMediaId();
		TaskItem updated = items.updateReferencesIfAvailable(new UpdateTaskItemReferencesMutation(
			itemId, command.expectedRevision(), text, audioUrl, videoUrl, audioId, videoId,
			operationId, actor.userId(), displayName(actor), Instant.now(clock)
		)).orElseThrow(this::stale);
		scheduleOldMedia(current, audioUrl, videoUrl, operationId);
		return updated;
	}

	public TaskItem delete(String itemId, long expectedRevision, String operationId, PlatformPrincipal actor) {
		requireAdmin(actor);
		requireAvailable(itemId, expectedRevision);
		TaskItem deleted = items.deleteAvailableIfCurrent(itemId, expectedRevision).orElseThrow(this::stale);
		scheduleMedia(deleted, operationId + ":delete");
		return deleted;
	}

	private TaskItem requireAvailable(String itemId, long revision) {
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
		if (item.getStatus() != TaskItemStatus.AVAILABLE || item.getRevision() != revision) throw stale();
		return item;
	}

	private void validate(TaskConfiguration configuration, String text, String audio, String video) {
		if (configuration == null) throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		if (text == null && audio == null && video == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_REFERENCE_REQUIRED", "每条数据至少需要一种参考内容");
		}
		if (text != null && !configuration.getReferenceTypes().contains(ReferenceType.TEXT)
			|| audio != null && !configuration.getReferenceTypes().contains(ReferenceType.AUDIO)
			|| video != null && !configuration.getReferenceTypes().contains(ReferenceType.VIDEO)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_TYPE_NOT_ENABLED", "参考内容类型未在任务中启用");
		}
	}

	private void scheduleOldMedia(TaskItem current, String audioUrl, String videoUrl, String operationId) {
		List<String> ids = new ArrayList<>();
		if (changed(current.getReferenceAudioUrl(), audioUrl)) add(ids, current.getReferenceAudioMediaId());
		if (changed(current.getReferenceVideoUrl(), videoUrl)) add(ids, current.getReferenceVideoMediaId());
		schedule(current.getId(), operationId + ":old-references", ids);
	}

	private void scheduleMedia(TaskItem item, String operationId) {
		List<String> ids = new ArrayList<>();
		add(ids, item.getReferenceAudioMediaId());
		add(ids, item.getReferenceVideoMediaId());
		schedule(item.getId(), operationId, ids);
	}

	private void schedule(String itemId, String operationId, List<String> ids) {
		if (ids.isEmpty()) return;
		List<String> paths = ids.stream().map(assets::findById).flatMap(java.util.Optional::stream)
			.map(MediaAsset::getRelativePath).filter(Objects::nonNull).toList();
		cleanup.scheduleAndTry(itemId, operationId, paths, ids);
	}

	private void add(List<String> values, String value) { if (value != null && !value.isBlank()) values.add(value); }
	private boolean changed(String previous, String next) { return !Objects.equals(trim(previous), trim(next)); }
	private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String displayName(PlatformPrincipal actor) { return actor.username() == null ? actor.name() : actor.username(); }
	private void requireAdmin(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}
	private ApiException stale() {
		return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "任务条目状态已变化，请刷新后重试");
	}
}

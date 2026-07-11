package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemActionService {
	private final TaskItemStore items;
	private final TaskPoolService pool;
	private final RecordingMediaStorage storage;
	private final MediaAssetStore assets;

	public TaskItemActionService(
		TaskItemStore items,
		TaskPoolService pool,
		RecordingMediaStorage storage,
		MediaAssetStore assets
	) {
		this.items = items;
		this.pool = pool;
		this.storage = storage;
		this.assets = assets;
	}

	public TaskItemActionResult release(
		String itemId,
		String operationId,
		long expectedRevision,
		PlatformPrincipal actor
	) {
		TaskItem item = requireItem(itemId);
		TaskItemResult current = item.getCurrentResult();
		SubmittedRecording audio = current == null ? null : current.audio();
		TaskItemActionResult result = pool.release(itemId, operationId, expectedRevision, actor);
		if (audio != null) {
			storage.delete(audio.relativePath());
			assets.deleteById(audio.mediaId());
		}
		return result;
	}

	public TaskItemActionResult reject(
		String itemId,
		String operationId,
		long expectedRevision,
		String reason,
		PlatformPrincipal actor
	) {
		return pool.reject(itemId, operationId, expectedRevision, reason, actor);
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}
}

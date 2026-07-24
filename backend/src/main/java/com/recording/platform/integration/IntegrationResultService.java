package com.recording.platform.integration;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAccessService;
import com.recording.platform.media.ReadableMedia;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IntegrationResultService {
	private final TaskItemStore items;
	private final MediaAccessService media;

	public IntegrationResultService(TaskItemStore items, MediaAccessService media) {
		this.items = items;
		this.media = media;
	}

	public IntegrationResultView get(String itemId) {
		return IntegrationResultView.from(requireItem(itemId));
	}

	public ReadableMedia openAudio(String itemId) {
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.COMPLETED) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"INTEGRATION_RESULT_NOT_COMPLETED",
				"任务条目尚未完成"
			);
		}
		TaskItemResult result = item.getCurrentResult();
		SubmittedRecording audio = result == null ? null : result.audio();
		if (audio == null || audio.mediaId() == null || audio.mediaId().isBlank()) {
			throw new ApiException(
				HttpStatus.NOT_FOUND,
				"INTEGRATION_RESULT_AUDIO_NOT_FOUND",
				"完成结果没有录音"
			);
		}
		return media.openIntegrationRecording(audio.mediaId(), item.getId());
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"TASK_ITEM_NOT_FOUND",
				"任务条目不存在"
			));
	}
}

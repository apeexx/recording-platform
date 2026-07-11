package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskItemActionServiceTests {
	@Test
	void releaseDoesNotHideMediaMetadataDeletionFailure() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskPoolService pool = org.mockito.Mockito.mock(TaskPoolService.class);
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		SubmittedRecording audio = new SubmittedRecording(
			"media-1", "recordings/TASK-001/I000001/current.wav", RecordingFormat.WAV,
			32044, 16000, 1, 1000
		);
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setCurrentResult(new TaskItemResult(audio, null));
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		when(pool.release("item-1", "release-1", 1, null)).thenReturn(
			new TaskItemActionResult("item-1", TaskItemStatus.AVAILABLE, 2, null, null)
		);
		doThrow(new IllegalStateException("simulated media metadata delete failure"))
			.when(assets).deleteById("media-1");
		TaskItemActionService service = new TaskItemActionService(items, pool, storage, assets);

		assertThatThrownBy(() -> service.release("item-1", "release-1", 1, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("metadata delete failure");
	}
}

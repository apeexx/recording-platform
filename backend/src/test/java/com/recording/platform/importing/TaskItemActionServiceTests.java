package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.media.InMemoryMediaCleanupJobStore;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaCleanupService;
import com.recording.platform.media.MediaCleanupStatus;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RecordingRetirement;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.api.ApiException;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;

class TaskItemActionServiceTests {
	@Test
	void releaseCleanupFailureIsRetriedByOperationReplay() {
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
		TaskItem released = new TaskItem();
		released.setId("item-1");
		when(items.findById("item-1")).thenReturn(Optional.of(item), Optional.of(released));
		TaskItemActionResult result = new TaskItemActionResult(
			"item-1", TaskItemStatus.AVAILABLE, 2, null, null
		);
		when(pool.release("item-1", "release-1", 1, null)).thenReturn(result);
		RecordingRetirement retirement = org.mockito.Mockito.mock(RecordingRetirement.class);
		when(storage.stageRetirement(audio.relativePath())).thenReturn(retirement);
		when(retirement.deferCleanup()).thenReturn("temp/backups/release-old.wav");
		doThrow(new IllegalStateException("simulated backup delete failure"))
			.doNothing()
			.when(storage).delete("temp/backups/release-old.wav");
		InMemoryMediaCleanupJobStore cleanupJobs = new InMemoryMediaCleanupJobStore();
		MediaCleanupService cleanup = new MediaCleanupService(
			cleanupJobs,
			storage,
			assets,
			Runnable::run,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		TaskItemActionService service = new TaskItemActionService(items, pool, storage, cleanup);

		assertThat(service.release("item-1", "release-1", 1, null)).isEqualTo(result);
		assertThat(cleanupJobs.findByItemIdAndOperationId("item-1", "release-1").orElseThrow().getStatus())
			.isEqualTo(MediaCleanupStatus.PENDING);

		assertThat(service.release("item-1", "release-1", 1, null)).isEqualTo(result);
		assertThat(cleanupJobs.findByItemIdAndOperationId("item-1", "release-1").orElseThrow().getStatus())
			.isEqualTo(MediaCleanupStatus.COMPLETED);
		verify(retirement, never()).rollback();
		verify(storage, times(2)).delete("temp/backups/release-old.wav");
		verify(assets, times(2)).deleteById("media-1");
	}

	@Test
	void batchReleaseRunsMediaSafeReleasePerItemAndReturnsPartialFailures() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskPoolService pool = org.mockito.Mockito.mock(TaskPoolService.class);
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaCleanupService cleanup = org.mockito.Mockito.mock(MediaCleanupService.class);
		TaskItem first = new TaskItem();
		first.setId("item-1");
		TaskItem second = new TaskItem();
		second.setId("item-2");
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		when(pool.release("item-1", "batch-release:0", 2, admin())).thenReturn(
			new TaskItemActionResult("item-1", TaskItemStatus.AVAILABLE, 3, null, null)
		);
		when(pool.release("item-2", "batch-release:1", 4, admin())).thenThrow(
			new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "状态已变化")
		);
		TaskItemActionService service = new TaskItemActionService(items, pool, storage, cleanup);

		List<BatchItemResult> results = service.batchRelease(
			"batch-release",
			List.of(new BatchItemCommand("item-1", 2, null), new BatchItemCommand("item-2", 4, null)),
			admin()
		);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(0).revision()).isEqualTo(3);
		assertThat(results.get(1).code()).isEqualTo("STALE_STATE");
		verify(cleanup).retry("item-1", "batch-release:0");
		verify(cleanup).retry("item-2", "batch-release:1");
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

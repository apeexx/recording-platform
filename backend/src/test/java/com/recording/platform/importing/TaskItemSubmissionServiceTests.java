package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.PreparedRecording;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RecordingReplacement;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TaskItemSubmissionServiceTests {
	@Test
	void concurrentDuplicateOperationOnlyTouchesTheStableCurrentFileOnce() throws Exception {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versions = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskPoolService pool = org.mockito.Mockito.mock(TaskPoolService.class);
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		AtomicBoolean committed = new AtomicBoolean();
		when(items.findById("item-1")).thenAnswer((ignored) -> Optional.of(item(committed.get())));
		TaskRecord task = new TaskRecord();
		task.setTaskCode("TASK-001");
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		TaskVersion version = new TaskVersion();
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		SubmittedRecording recording = new SubmittedRecording(
			"media-1", "recordings/TASK-001/I000001/current.wav", RecordingFormat.WAV,
			32044, 16000, 1, 1000
		);
		PreparedRecording prepared = new PreparedRecording(recording, Path.of("temp.wav"));
		when(storage.prepare(any(), eq(version), eq("TASK-001"), eq("I000001"))).thenReturn(prepared);
		RecordingReplacement replacement = org.mockito.Mockito.mock(RecordingReplacement.class);
		when(storage.activate(prepared, null)).thenReturn(replacement);
		when(assets.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		TaskItemActionResult result = new TaskItemActionResult(
			"item-1", TaskItemStatus.REVIEW_PENDING, 2, "assignment-1",
			new TaskItemResult(recording, null)
		);
		when(pool.submit(eq("item-1"), any(), any())).thenAnswer((ignored) -> {
			committed.set(true);
			return result;
		});
		TaskItemSubmissionService service = new TaskItemSubmissionService(
			items,
			tasks,
			versions,
			pool,
			storage,
			assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		SubmitTaskItemForm form = new SubmitTaskItemForm("submit-1", "assignment-1", 1, null);
		MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] {1});
		PlatformPrincipal collector = new PlatformPrincipal(
			"session-1", "collector-1", "collector", "张三",
			UserRole.COLLECTOR, SessionType.MINIPROGRAM, false
		);

		var executor = Executors.newFixedThreadPool(2);
		try {
			var futures = executor.invokeAll(List.of(
				() -> service.submit("item-1", form, audio, collector),
				() -> service.submit("item-1", form, audio, collector)
			));
			assertThat(futures.get(0).get()).isEqualTo(result);
			assertThat(futures.get(1).get()).isEqualTo(result);
		} finally {
			executor.shutdownNow();
		}

		verify(storage, times(1)).prepare(any(), eq(version), eq("TASK-001"), eq("I000001"));
		verify(storage, times(1)).activate(prepared, null);
		verify(replacement, times(1)).complete();
		verify(assets, times(1)).save(any());
	}

	@Test
	void cleanupFailureAfterMongoCommitDoesNotRollbackTheCommittedCurrentFile() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versions = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskPoolService pool = org.mockito.Mockito.mock(TaskPoolService.class);
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		when(items.findById("item-1")).thenReturn(Optional.of(item(false)));
		TaskRecord task = new TaskRecord();
		task.setTaskCode("TASK-001");
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		TaskVersion version = new TaskVersion();
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		SubmittedRecording recording = new SubmittedRecording(
			"media-1", "recordings/TASK-001/I000001/current.wav", RecordingFormat.WAV,
			32044, 16000, 1, 1000
		);
		PreparedRecording prepared = new PreparedRecording(recording, Path.of("temp.wav"));
		when(storage.prepare(any(), eq(version), eq("TASK-001"), eq("I000001"))).thenReturn(prepared);
		RecordingReplacement replacement = org.mockito.Mockito.mock(RecordingReplacement.class);
		when(storage.activate(prepared, null)).thenReturn(replacement);
		when(assets.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		TaskItemActionResult committed = new TaskItemActionResult(
			"item-1", TaskItemStatus.REVIEW_PENDING, 2, "assignment-1", new TaskItemResult(recording, null)
		);
		when(pool.submit(eq("item-1"), any(), any())).thenReturn(committed);
		doThrow(new com.recording.platform.api.ApiException(
			org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
			"MEDIA_DELETE_FAILED",
			"媒体文件暂时无法删除"
		)).when(replacement).complete();
		TaskItemSubmissionService service = new TaskItemSubmissionService(
			items, tasks, versions, pool, storage, assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		SubmitTaskItemForm form = new SubmitTaskItemForm("submit-1", "assignment-1", 1, null);
		MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] {1});
		PlatformPrincipal collector = new PlatformPrincipal(
			"session-1", "collector-1", "collector", "张三",
			UserRole.COLLECTOR, SessionType.MINIPROGRAM, false
		);

		assertThatThrownBy(() -> service.submit("item-1", form, audio, collector))
			.isInstanceOfSatisfying(com.recording.platform.api.ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("MEDIA_DELETE_FAILED")
			);
		verify(replacement, never()).rollback();
		verify(assets, never()).deleteById("media-1");
	}

	private TaskItem item(boolean committed) {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskId("task-1");
		item.setTaskVersionId("version-1");
		item.setItemCode("I000001");
		item.setStatus(committed ? TaskItemStatus.REVIEW_PENDING : TaskItemStatus.RECORDING_PENDING);
		item.setCollectorId("collector-1");
		item.setAssignmentId("assignment-1");
		item.setRevision(committed ? 2 : 1);
		if (committed) {
			OperationHistory operation = new OperationHistory();
			operation.setOperationId("submit-1");
			operation.setActorUserId("collector-1");
			item.getOperations().add(operation);
		}
		return item;
	}
}

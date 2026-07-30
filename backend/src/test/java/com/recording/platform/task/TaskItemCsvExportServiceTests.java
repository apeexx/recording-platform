package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskItemCsvExportService;
import com.recording.platform.task.service.TaskItemFilter;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TaskItemCsvExportServiceTests {
	@Test
	void exportsBusinessFieldsWithBomFormulaProtectionAndCompletedFinalText() throws Exception {
		TaskStore tasks = mock(TaskStore.class);
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		TaskRecord task = task();
		TaskItem completed = item(1, TaskItemStatus.COMPLETED, "=source-1", "=原始文本");
		completed.setReviewFinalAnswer("+审核最终文本");
		completed.setCollectorId("MINI-1");
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(items.findAllByTaskId(eq("task-1"), any(TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(completed)));
		when(users.findAllByIdIn(any())).thenReturn(List.of(identity("MINI-1", "采集员一")));
		var service = new TaskItemCsvExportService(tasks, items, users);
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		service.write("task-1", TaskItemFilter.all(), output);

		byte[] bytes = output.toByteArray();
		assertThat(bytes).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
		String csv = new String(bytes, StandardCharsets.UTF_8);
		assertThat(csv).contains(
			"taskCode,taskName,itemCode,sourcePlatform,sourceItemId,status,statusLabel",
			"T000001", "普通话录音", "T000001-0000001", "BYTEDANCE_AIDP",
			"'=source-1", "采集员一", "'=原始文本", "'+审核最终文本",
			"true", "12000", "30000"
		);
		assertThat(csv).contains(
			"https://cdn.example.com/reference.wav",
			"https://example.com/video"
		);
		assertThat(csv).doesNotContain(
			"X-Amz-Signature", "secret-signature", "access_token", "secret-token"
		);
		assertThat(csv).doesNotContain("relativePath", "media-1", "recording.wav");
	}

	@Test
	void writesEveryPageInsteadOfStoppingAtTheApiPageLimit() throws Exception {
		TaskStore tasks = mock(TaskStore.class);
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task()));
		List<TaskItem> first = java.util.stream.LongStream.rangeClosed(1, 100)
			.mapToObj(sequence -> item(sequence, TaskItemStatus.SUBMITTED, null, null)).toList();
		List<TaskItem> second = List.of(item(101, TaskItemStatus.SUBMITTED, null, null));
		when(items.findAllByTaskId(eq("task-1"), any(TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(first), new PageImpl<>(second));
		when(users.findAllByIdIn(any())).thenReturn(List.of());
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		new TaskItemCsvExportService(tasks, items, users)
			.write("task-1", TaskItemFilter.all(), output);

		String csv = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(csv.lines().filter(line -> line.contains("T000001-")).count()).isEqualTo(101);
	}

	@Test
	void emptyExportStillContainsTheFixedHeader() throws Exception {
		TaskStore tasks = mock(TaskStore.class);
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task()));
		when(items.findAllByTaskId(eq("task-1"), any(TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		new TaskItemCsvExportService(tasks, items, users)
			.write("task-1", TaskItemFilter.all(), output);

		assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8).lines()).hasSize(1);
	}

	private TaskRecord task() {
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setTaskCode("T000001");
		task.setName("普通话录音");
		return task;
	}

	private TaskItem item(long sequence, TaskItemStatus status, String sourceItemId, String text) {
		TaskItem item = new TaskItem();
		item.setId("item-" + sequence);
		item.setTaskId("task-1");
		item.setSequence(sequence);
		item.setItemCode("T000001-" + String.format("%07d", sequence));
		item.setStatus(status);
		item.setSourcePlatform(sourceItemId == null ? null : "BYTEDANCE_AIDP");
		item.setSourceItemId(sourceItemId);
		item.setReferenceText("参考文本");
		item.setReferenceAudioUrl(
			"https://cdn.example.com/reference.wav?X-Amz-Signature=secret-signature#player"
		);
		item.setReferenceVideoUrl("https://example.com/video?access_token=secret-token");
		item.setFirstSubmittedAt(Instant.parse("2026-07-30T01:00:00Z"));
		item.setLatestSubmittedAt(Instant.parse("2026-07-30T02:00:00Z"));
		item.setCurrentResult(new TaskItemResult(
			new SubmittedRecording("media-1", "recording.wav", RecordingFormat.WAV, 10, 16000, 1, 12_000),
			text
		));
		item.setReferenceVideoDurationMillis(30_000L);
		return item;
	}

	private IdentityUser identity(String id, String name) {
		return new IdentityUser(
			id, UserType.MINIPROGRAM, "collector", name, UserRole.COLLECTOR,
			UserStatus.ACTIVE, false, Instant.EPOCH, Instant.EPOCH
		);
	}
}

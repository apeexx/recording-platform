package com.recording.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAccessService;
import com.recording.platform.media.ReadableMedia;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IntegrationResultServiceTests {
	private final TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
	private final MediaAccessService media = org.mockito.Mockito.mock(MediaAccessService.class);
	private final IntegrationResultService service = new IntegrationResultService(items, media);

	@Test
	void unfinishedItemNeverExposesItsCurrentDraftResult() {
		TaskItem item = item(TaskItemStatus.REVIEW_PENDING);
		item.setCurrentResult(new TaskItemResult(recording("media-draft"), "审核中的文字"));
		when(items.findById("item-1")).thenReturn(Optional.of(item));

		IntegrationResultView result = service.get("item-1");

		assertThat(result.itemId()).isEqualTo("item-1");
		assertThat(result.taskId()).isEqualTo("task-1");
		assertThat(result.itemCode()).isEqualTo("T000001-0000001");
		assertThat(result.status()).isEqualTo(TaskItemStatus.REVIEW_PENDING);
		assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-07-24T02:03:04Z"));
		assertThat(result.text()).isNull();
		assertThat(result.audioAvailable()).isFalse();
	}

	@Test
	void completedItemExposesOnlyCurrentTextAndAudioAvailability() {
		TaskItem item = item(TaskItemStatus.COMPLETED);
		item.setCurrentResult(new TaskItemResult(recording("media-current"), "最终文字"));
		when(items.findById("item-1")).thenReturn(Optional.of(item));

		IntegrationResultView result = service.get("item-1");

		assertThat(result.text()).isEqualTo("最终文字");
		assertThat(result.audioAvailable()).isTrue();
	}

	@Test
	void completedTextOnlyItemReportsNoAudio() {
		TaskItem item = item(TaskItemStatus.COMPLETED);
		item.setCurrentResult(new TaskItemResult(null, "纯文本结果"));
		when(items.findById("item-1")).thenReturn(Optional.of(item));

		IntegrationResultView result = service.get("item-1");

		assertThat(result.text()).isEqualTo("纯文本结果");
		assertThat(result.audioAvailable()).isFalse();
	}

	@Test
	void audioReadRequiresCompletedItemWithCurrentRecording() {
		TaskItem unfinished = item(TaskItemStatus.SUBMITTED);
		unfinished.setCurrentResult(new TaskItemResult(recording("media-draft"), null));
		when(items.findById("item-unfinished")).thenReturn(Optional.of(unfinished));

		assertApiError(
			() -> service.openAudio("item-unfinished"),
			HttpStatus.CONFLICT,
			"INTEGRATION_RESULT_NOT_COMPLETED"
		);

		TaskItem textOnly = item(TaskItemStatus.COMPLETED);
		textOnly.setCurrentResult(new TaskItemResult(null, "纯文本结果"));
		when(items.findById("item-text-only")).thenReturn(Optional.of(textOnly));

		assertApiError(
			() -> service.openAudio("item-text-only"),
			HttpStatus.NOT_FOUND,
			"INTEGRATION_RESULT_AUDIO_NOT_FOUND"
		);
	}

	@Test
	void completedAudioReadUsesCurrentRecordingMediaBoundary() {
		TaskItem item = item(TaskItemStatus.COMPLETED);
		item.setCurrentResult(new TaskItemResult(recording("media-current"), null));
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		ReadableMedia readable = new ReadableMedia(Path.of("current.mp3"), "audio/mpeg", 12);
		when(media.openIntegrationRecording("media-current", "item-1")).thenReturn(readable);

		assertThat(service.openAudio("item-1")).isSameAs(readable);
		verify(media).openIntegrationRecording("media-current", "item-1");
	}

	@Test
	void missingItemUsesExistingNotFoundContract() {
		when(items.findById("missing")).thenReturn(Optional.empty());

		assertApiError(
			() -> service.get("missing"),
			HttpStatus.NOT_FOUND,
			"TASK_ITEM_NOT_FOUND"
		);
		assertApiError(
			() -> service.openAudio("missing"),
			HttpStatus.NOT_FOUND,
			"TASK_ITEM_NOT_FOUND"
		);
	}

	private TaskItem item(TaskItemStatus status) {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskId("task-1");
		item.setItemCode("T000001-0000001");
		item.setStatus(status);
		item.setUpdatedAt(Instant.parse("2026-07-24T02:03:04Z"));
		return item;
	}

	private SubmittedRecording recording(String mediaId) {
		return new SubmittedRecording(
			mediaId,
			"T000001/T000001-0000001.mp3",
			RecordingFormat.MP3,
			12,
			16000,
			1,
			1000
		);
	}

	private void assertApiError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		HttpStatus status,
		String code
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.getStatus()).isEqualTo(status);
				assertThat(exception.getCode()).isEqualTo(code);
			});
	}
}

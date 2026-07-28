package com.recording.platform.review.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAccessService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;

class ReviewAiJobServiceTests {
	@Test
	void currentReviewerCreatesPersistentAudioJobWithSourceSnapshot() {
		ReviewAiJobRepository jobs = mock(ReviewAiJobRepository.class);
		ReviewAiConfigService configs = mock(ReviewAiConfigService.class);
		ReviewAiProvider provider = mock(ReviewAiProvider.class);
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item();
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		ReviewAiConfig config = ReviewAiDefaults.config("task-1");
		config.setAudio(new ReviewAiStageConfig(
			true, "qwen3.5-omni-plus", "prompt", 0.1, 0.8, 1200, 60000
		));
		when(configs.get("task-1", reviewer())).thenReturn(config);
		when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		TaskExecutor noOpExecutor = command -> { };
		ReviewAiJobService service = new ReviewAiJobService(
			jobs, configs, provider, items, mock(MediaAccessService.class),
			noOpExecutor, mock(MongoTemplate.class),
			Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC)
		);

		ReviewAiJobView result = service.create(
			"item-1", ReviewAiJobType.AUDIO_TRANSCRIBE, 7, "operation-1", reviewer()
		);

		assertThat(result.status()).isEqualTo(ReviewAiJobStatus.PENDING);
		assertThat(result.itemRevision()).isEqualTo(7);
		verify(provider).ensureConfigured();
		verify(jobs).save(org.mockito.ArgumentMatchers.argThat(job ->
			"media-1".equals(job.getSourceMediaId())
				&& "review-assignment-1".equals(job.getReviewAssignmentId())
		));
	}

	@Test
	void oversizedAudioIsRejectedBeforeJobPersistenceAndBase64Expansion() {
		ReviewAiJobRepository jobs = mock(ReviewAiJobRepository.class);
		ReviewAiConfigService configs = mock(ReviewAiConfigService.class);
		ReviewAiProvider provider = mock(ReviewAiProvider.class);
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item();
		item.setCurrentResult(new TaskItemResult(
			new SubmittedRecording(
				"media-1", "T000001/T000001-0000001.wav", RecordingFormat.WAV,
				20L * 1024 * 1024 + 1, 16000, 1, 1000
			),
			"原始文本"
		));
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		ReviewAiConfig config = ReviewAiDefaults.config("task-1");
		config.setAudio(new ReviewAiStageConfig(
			true, "qwen3.5-omni-plus", "prompt", 0.1, 0.8, 1200, 60000
		));
		when(configs.get("task-1", reviewer())).thenReturn(config);
		ReviewAiJobService service = new ReviewAiJobService(
			jobs, configs, provider, items, mock(MediaAccessService.class),
			command -> { }, mock(MongoTemplate.class),
			Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC)
		);

		assertThatThrownBy(() -> service.create(
			"item-1", ReviewAiJobType.AUDIO_TRANSCRIBE, 7, "operation-large", reviewer()
		)).isInstanceOfSatisfying(ApiException.class, error -> {
			assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
			assertThat(error.getCode()).isEqualTo("REVIEW_AI_AUDIO_TOO_LARGE");
		});
	}

	private TaskItem item() {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskId("task-1");
		item.setStatus(TaskItemStatus.REVIEW_PENDING);
		item.setRevision(7);
		item.setReviewerId("reviewer-1");
		item.setReviewAssignmentId("review-assignment-1");
		item.setCurrentResult(new TaskItemResult(
			new SubmittedRecording(
				"media-1", "T000001/T000001-0000001.wav", RecordingFormat.WAV,
				10, 16000, 1, 1000
			),
			"原始文本"
		));
		return item;
	}

	private PlatformPrincipal reviewer() {
		return new PlatformPrincipal(
			"session-1", "reviewer-1", "reviewer", "审核员",
			UserRole.REVIEWER, SessionType.WEB, false
		);
	}
}

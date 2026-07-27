package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.TaskItem;
import org.junit.jupiter.api.Test;

class ReferenceMediaDurationPolicyTests {
	private final ReferenceMediaDurationPolicy policy = new ReferenceMediaDurationPolicy();

	@Test
	void requiresPositiveDurationForEveryExistingReferenceSource() {
		TaskItem item = new TaskItem();
		item.setReferenceAudioUrl("https://example.test/reference.mp3");

		assertThatThrownBy(() -> policy.validate(item, 0L, 0L))
			.isInstanceOfSatisfying(ApiException.class, error -> {
				assertThat(error.getStatus().value()).isEqualTo(422);
				assertThat(error.getCode()).isEqualTo("REFERENCE_DURATION_REQUIRED");
			});
	}

	@Test
	void rejectsDurationWhenTheMatchingReferenceSourceDoesNotExist() {
		TaskItem item = new TaskItem();

		assertThatThrownBy(() -> policy.validate(item, 1_000L, 0L))
			.isInstanceOfSatisfying(ApiException.class, error -> {
				assertThat(error.getStatus().value()).isEqualTo(422);
				assertThat(error.getCode()).isEqualTo("REFERENCE_DURATION_NOT_APPLICABLE");
			});
	}

	@Test
	void acceptsOneSecondMeasurementDriftAndKeepsTheFirstConfirmedDuration() {
		TaskItem item = new TaskItem();
		item.setReferenceAudioUrl("https://example.test/reference.mp3");
		item.setReferenceAudioDurationMillis(10_000L);

		ReferenceMediaDurationPolicy.Durations result = policy.validate(item, 11_000L, 0L);

		assertThat(result.audioMillis()).isEqualTo(10_000L);
		assertThat(result.videoMillis()).isZero();
	}

	@Test
	void rejectsMeasurementDriftGreaterThanOneSecond() {
		TaskItem item = new TaskItem();
		item.setReferenceVideoUrl("https://example.test/reference.mp4");
		item.setReferenceVideoDurationMillis(10_000L);

		assertThatThrownBy(() -> policy.validate(item, 0L, 11_001L))
			.isInstanceOfSatisfying(ApiException.class, error -> {
				assertThat(error.getStatus().value()).isEqualTo(409);
				assertThat(error.getCode()).isEqualTo("REFERENCE_DURATION_MISMATCH");
			});
	}
}

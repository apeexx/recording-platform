package com.recording.platform.review.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewAiConfigServiceTests {
	private final ReviewAiConfigRepository configs = mock(ReviewAiConfigRepository.class);
	private final TaskStore tasks = mock(TaskStore.class);
	private final ReviewAiConfigService service = new ReviewAiConfigService(
		configs, tasks, Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC)
	);

	@Test
	void missingTaskConfigReturnsSafeDisabledDefaults() {
		when(tasks.findById("task-1")).thenReturn(Optional.of(new TaskRecord()));
		when(configs.findById("task-1")).thenReturn(Optional.empty());

		ReviewAiConfig result = service.get("task-1", reviewer());

		assertThat(result.getAudio().enabled()).isFalse();
		assertThat(result.getAudio().model()).isEqualTo("qwen3.5-omni-plus");
		assertThat(result.getText().model()).isEqualTo("qwen3.5-plus");
		assertThat(result.getAudio().timeoutMs()).isEqualTo(60000);
	}

	@Test
	void invalidModelAndParametersUse422BusinessErrors() {
		when(tasks.findById("task-1")).thenReturn(Optional.of(new TaskRecord()));
		ReviewAiStageConfig invalid = new ReviewAiStageConfig(
			true, "unknown", "prompt", 0.1, 0.8, 1200, 60000
		);

		assertThatThrownBy(() -> service.put("task-1", invalid, invalid, admin()))
			.isInstanceOfSatisfying(ApiException.class, error ->
				assertThat(error.getCode()).isEqualTo("AI_MODEL_INVALID")
			);
	}

	@Test
	void providerExclusiveParameterBoundariesAreRejectedBeforeSaving() {
		when(tasks.findById("task-1")).thenReturn(Optional.of(new TaskRecord()));
		ReviewAiStageConfig temperatureTwo = new ReviewAiStageConfig(
			true, "qwen3.5-plus", "prompt", 2, 0.8, 1200, 60000
		);
		ReviewAiStageConfig topPZero = new ReviewAiStageConfig(
			true, "qwen3.5-plus", "prompt", 0.1, 0, 1200, 60000
		);
		ReviewAiStageConfig validAudio = new ReviewAiStageConfig(
			true, "qwen3.5-omni-plus", "prompt", 0.1, 0.8, 1200, 60000
		);

		assertThatThrownBy(() -> service.put("task-1", validAudio, temperatureTwo, admin()))
			.isInstanceOfSatisfying(ApiException.class, error ->
				assertThat(error.getCode()).isEqualTo("AI_TEMPERATURE_INVALID")
			);
		assertThatThrownBy(() -> service.put("task-1", validAudio, topPZero, admin()))
			.isInstanceOfSatisfying(ApiException.class, error ->
				assertThat(error.getCode()).isEqualTo("AI_TOP_P_INVALID")
			);
	}

	private PlatformPrincipal reviewer() {
		return principal("reviewer-1", UserRole.REVIEWER);
	}

	private PlatformPrincipal admin() {
		return principal("admin-1", UserRole.ADMIN);
	}

	private PlatformPrincipal principal(String id, UserRole role) {
		return new PlatformPrincipal("session-" + id, id, id, id, role, SessionType.WEB, false);
	}
}

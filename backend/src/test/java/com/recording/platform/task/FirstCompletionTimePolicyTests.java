package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.FirstCompletionTimePolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FirstCompletionTimePolicyTests {
	private static final Instant FIRST = Instant.parse("2026-07-01T02:00:00Z");
	private static final Instant LATER = Instant.parse("2026-07-03T04:00:00Z");

	@Test
	void firstCompletionIsCapturedOnceAndNeverOverwrittenByRecompletion() {
		assertThat(FirstCompletionTimePolicy.resolve(null, TaskItemStatus.COMPLETED, FIRST))
			.isEqualTo(FIRST);
		assertThat(FirstCompletionTimePolicy.resolve(FIRST, TaskItemStatus.COMPLETED, LATER))
			.isEqualTo(FIRST);
	}

	@Test
	void administratorRollbackKeepsTheOriginalCompletionTime() {
		assertThat(FirstCompletionTimePolicy.resolve(FIRST, TaskItemStatus.REWORK_PENDING, LATER))
			.isEqualTo(FIRST);
	}
}

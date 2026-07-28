package com.recording.platform.review.ai;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class ReviewAiRecoveryRunner implements ApplicationRunner {
	private final ReviewAiJobService jobs;

	ReviewAiRecoveryRunner(ReviewAiJobService jobs) {
		this.jobs = jobs;
	}

	@Override
	public void run(ApplicationArguments args) {
		jobs.recover();
	}
}

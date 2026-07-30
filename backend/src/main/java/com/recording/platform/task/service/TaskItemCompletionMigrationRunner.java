package com.recording.platform.task.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
	name = "recording.local-reset.enabled",
	havingValue = "false",
	matchIfMissing = true
)
final class TaskItemCompletionMigrationRunner implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(TaskItemCompletionMigrationRunner.class);
	private final TaskItemCompletionMigrationService service;

	TaskItemCompletionMigrationRunner(TaskItemCompletionMigrationService service) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		TaskItemCompletionMigrationService.Result result = service.backfillFirstCompletedAt();
		log.info(
			"Task item first completion migration completed: migrated={}, unresolved={}",
			result.migrated(), result.unresolved()
		);
	}
}

package com.recording.platform.task.store.mongo;

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
final class TaskPendingIndexMigrationRunner implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(TaskPendingIndexMigrationRunner.class);
	private final TaskPendingIndexMigrationService service;

	TaskPendingIndexMigrationRunner(TaskPendingIndexMigrationService service) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean uniqueIndexesRemoved = service.migrate();
		log.info("Task pending index migration completed: uniqueIndexesRemoved={}", uniqueIndexesRemoved);
	}
}

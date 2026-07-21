package com.recording.platform.task.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
final class TaskItemStatusMigrationRunner implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(TaskItemStatusMigrationRunner.class);
	private final TaskItemStatusMigrationService service;

	TaskItemStatusMigrationRunner(TaskItemStatusMigrationService service) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		long migrated = service.migrateLegacyUnassignedReviews();
		log.info("Legacy unassigned review status migration completed: migrated={}", migrated);
	}
}

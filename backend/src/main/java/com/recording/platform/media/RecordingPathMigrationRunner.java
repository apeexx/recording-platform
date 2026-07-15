package com.recording.platform.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "recording.path-migration.enabled", havingValue = "true")
final class RecordingPathMigrationRunner implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(RecordingPathMigrationRunner.class);
	private final RecordingPathMigrationService service;
	private final ConfigurableApplicationContext context;

	RecordingPathMigrationRunner(
		RecordingPathMigrationService service,
		ConfigurableApplicationContext context
	) {
		this.service = service;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		RecordingPathMigrationResult result = service.migrate();
		log.info(
			"Recording path migration completed: migrated={}, deduplicated={}",
			result.migrated(),
			result.deduplicated()
		);
		int exitCode = SpringApplication.exit(context, () -> 0);
		System.exit(exitCode);
	}
}

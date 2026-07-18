package com.recording.platform.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
@ConditionalOnProperty(name = "recording.local-reset.enabled", havingValue = "true")
final class LocalDataResetExitRunner implements ApplicationRunner {
	private final ConfigurableApplicationContext context;

	LocalDataResetExitRunner(ConfigurableApplicationContext context) {
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		SpringApplication.exit(context, () -> 0);
	}
}

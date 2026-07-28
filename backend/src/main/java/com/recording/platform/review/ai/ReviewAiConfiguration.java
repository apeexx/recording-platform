package com.recording.platform.review.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class ReviewAiConfiguration {
	@Bean
	DashScopeSettings dashScopeSettings(
		@Value("${recording.dashscope.api-key:}") String apiKey,
		@Value("${recording.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl
	) {
		return new DashScopeSettings(apiKey, baseUrl);
	}

	@Bean
	ReviewAiProvider reviewAiProvider(DashScopeSettings settings, ObjectMapper mapper) {
		return new DashScopeReviewAiProvider(settings, mapper);
	}

	@Bean("reviewAiTaskExecutor")
	TaskExecutor reviewAiTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("review-ai-");
		executor.initialize();
		return executor;
	}
}

package com.recording.platform.review.ai;

public record ReviewAiStageConfig(
	boolean enabled,
	String model,
	String prompt,
	double temperature,
	double topP,
	int maxTokens,
	long timeoutMs
) { }

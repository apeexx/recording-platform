package com.recording.platform.review.ai;

public interface ReviewAiProvider {
	void ensureConfigured();
	ReviewAiProviderResult generate(
		ReviewAiJobType type,
		ReviewAiStageConfig config,
		String sourceText,
		byte[] audio,
		String audioFormat
	);
}

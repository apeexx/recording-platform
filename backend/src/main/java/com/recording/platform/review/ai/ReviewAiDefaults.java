package com.recording.platform.review.ai;

final class ReviewAiDefaults {
	static final String AUDIO_PROMPT = """
		请将音频忠实转写为简体中文，只输出转写正文。不要猜测或补充音频中不存在的信息；
		听不清的内容使用【听不清】标记，保留有意义的口语内容并补充必要的断句和标点。
		""".strip();
	static final String TEXT_PROMPT = """
		请只基于原文修正明显错字、断句和标点，只输出修订后的正文。
		不要添加、推断或改写原文中不存在的信息。
		""".strip();

	private ReviewAiDefaults() { }

	static ReviewAiConfig config(String taskId) {
		ReviewAiConfig config = new ReviewAiConfig();
		config.setTaskId(taskId);
		config.setAudio(new ReviewAiStageConfig(
			false, "qwen3.5-omni-plus", AUDIO_PROMPT, 0.1, 0.8, 1200, 60000
		));
		config.setText(new ReviewAiStageConfig(
			false, "qwen3.5-plus", TEXT_PROMPT, 0.1, 0.8, 1200, 60000
		));
		return config;
	}
}

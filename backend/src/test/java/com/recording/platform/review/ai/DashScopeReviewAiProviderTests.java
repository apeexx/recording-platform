package com.recording.platform.review.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DashScopeReviewAiProviderTests {
	@Test
	void parsesTextFromOpenAiCompatibleSseChunks() {
		DashScopeReviewAiProvider provider = new DashScopeReviewAiProvider(
			new DashScopeSettings("test-only", "https://example.invalid/v1"),
			new ObjectMapper()
		);
		String stream = """
			data: {"choices":[{"delta":{"content":"忠实"}}]}

			data: {"choices":[{"delta":{"content":"转写"}}]}

			data: [DONE]
			""";

		assertThat(provider.parseSse(stream)).isEqualTo("忠实转写");
	}
}

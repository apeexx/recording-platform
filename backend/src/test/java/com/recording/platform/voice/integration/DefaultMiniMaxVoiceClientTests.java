package com.recording.platform.voice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.voice.VoiceGenerationException;
import org.junit.jupiter.api.Test;

class DefaultMiniMaxVoiceClientTests {

	@Test
	void rejectsMissingApiKeyBeforeCallingRemoteService() {
		DefaultMiniMaxVoiceClient client = new DefaultMiniMaxVoiceClient(
			new MiniMaxSettings("", "https://api.minimax.io")
		);

		assertThatThrownBy(() -> client.ensureApiKeyConfigured())
			.isInstanceOf(VoiceGenerationException.class)
			.hasMessage("请先配置 MINIMAX_API_KEY");
	}

	@Test
	void parsesHexAudioPayloadFromMiniMaxJsonResponse() {
		byte[] audio = MiniMaxAudioParser.parseAudioBytes("""
			{
			  "base_resp": {"status_code": 0},
			  "data": {"audio": "01020aff"}
			}
			""");

		assertThat(audio).containsExactly(1, 2, 10, -1);
	}
}

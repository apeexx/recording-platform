package com.recording.platform.voice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.recording.platform.voice.VoiceGenerationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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

	@Test
	void cloneVoiceSendsNumericFileId() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://api.minimaxi.com")
			.defaultHeader("Authorization", "Bearer test-key");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		DefaultMiniMaxVoiceClient client = new DefaultMiniMaxVoiceClient(
			new MiniMaxSettings("test-key", "https://api.minimaxi.com"),
			builder.build()
		);

		server.expect(requestTo("https://api.minimaxi.com/v1/voice_clone"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{"file_id":123456789012345680,"voice_id":"Arabic_man"}
				"""))
			.andRespond(withSuccess("""
				{"base_resp":{"status_code":0,"status_msg":"success"}}
				""", MediaType.APPLICATION_JSON));

		client.cloneVoice("123456789012345680", "Arabic_man");

		server.verify();
	}

	@Test
	void cloneVoiceIncludesMiniMaxStatusSummaryWhenRejected() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://api.minimaxi.com")
			.defaultHeader("Authorization", "Bearer test-key");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		DefaultMiniMaxVoiceClient client = new DefaultMiniMaxVoiceClient(
			new MiniMaxSettings("test-key", "https://api.minimaxi.com"),
			builder.build()
		);

		server.expect(requestTo("https://api.minimaxi.com/v1/voice_clone"))
			.andRespond(withSuccess("""
				{"base_resp":{"status_code":2013,"status_msg":"voice_id already exists"}}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.cloneVoice("123456789012345680", "Arabic_man"))
			.isInstanceOf(VoiceGenerationException.class)
			.hasMessageContaining("MiniMax 音色克隆失败")
			.hasMessageContaining("voice_id already exists");

		server.verify();
	}
}

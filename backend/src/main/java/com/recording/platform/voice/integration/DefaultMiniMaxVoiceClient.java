package com.recording.platform.voice.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.voice.VoiceGenerationException;
import com.recording.platform.voice.dto.SynthesisRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

public class DefaultMiniMaxVoiceClient implements MiniMaxVoiceClient {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private final MiniMaxSettings settings;
	private final RestClient restClient;

	public DefaultMiniMaxVoiceClient(MiniMaxSettings settings) {
		this.settings = settings;
		this.restClient = RestClient.builder()
			.baseUrl(settings.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(settings.apiKey()))
			.build();
	}

	public void ensureApiKeyConfigured() {
		if (!StringUtils.hasText(settings.apiKey())) {
			throw new VoiceGenerationException("请先配置 MINIMAX_API_KEY");
		}
	}

	@Override
	public String uploadAudio(MultipartFile audio, String purpose) {
		ensureApiKeyConfigured();
		try {
			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("purpose", purpose);
			body.add("file", new NamedByteArrayResource(audio.getBytes(), audio.getOriginalFilename()));
			Map<String, Object> response = restClient.post()
				.uri("/v1/files/upload")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body)
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<>() {
				});
			Object file = response == null ? null : response.get("file");
			if (file instanceof Map<?, ?> fileMap && fileMap.get("file_id") != null) {
				return fileMap.get("file_id").toString();
			}
			throw new VoiceGenerationException("MiniMax 文件上传失败");
		} catch (IOException exception) {
			throw new VoiceGenerationException("读取上传音频失败");
		}
	}

	@Override
	public void cloneVoice(String fileId, String voiceId) {
		ensureApiKeyConfigured();
		Map<String, Object> payload = Map.of("file_id", fileId, "voice_id", voiceId);
		Map<String, Object> response = restClient.post()
			.uri("/v1/voice_clone")
			.contentType(MediaType.APPLICATION_JSON)
			.body(payload)
			.retrieve()
			.body(new org.springframework.core.ParameterizedTypeReference<>() {
			});
		assertBaseResponseOk(response, "MiniMax 音色克隆失败");
	}

	@Override
	public MiniMaxSynthesisResult synthesize(SynthesisRequest request, String promptFileId) {
		ensureApiKeyConfigured();
		Map<String, Object> voiceSetting = new LinkedHashMap<>();
		voiceSetting.put("speed", request.speed());
		voiceSetting.put("vol", request.volume());
		voiceSetting.put("pitch", request.pitch());
		voiceSetting.put("voice_id", request.voiceId());
		if (StringUtils.hasText(promptFileId)) {
			voiceSetting.put("prompt_id", promptFileId);
		}
		Map<String, Object> payload = Map.of(
			"model", "speech-2.8-hd",
			"text", request.text(),
			"stream", false,
			"voice_setting", voiceSetting,
			"audio_setting", Map.of(
				"sample_rate", 32000,
				"bitrate", 128000,
				"format", "mp3",
				"channel", 1
			)
		);
		ResponseEntity<byte[]> response = restClient.post()
			.uri("/v1/t2a_v2")
			.contentType(MediaType.APPLICATION_JSON)
			.body(payload)
			.retrieve()
			.toEntity(byte[].class);
		MediaType contentType = response.getHeaders().getContentType();
		byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
		if (contentType != null && MediaType.APPLICATION_OCTET_STREAM.includes(contentType)) {
			return new MiniMaxSynthesisResult(body, "mp3", 0);
		}
		return new MiniMaxSynthesisResult(MiniMaxAudioParser.parseAudioBytes(new String(body)), "mp3", 0);
	}

	@Override
	public Map<String, Object> listVoices(boolean excludeSystemVoices) {
		ensureApiKeyConfigured();
		Map<String, Object> response = restClient.post()
			.uri("/v1/get_voice")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("voice_type", "all"))
			.retrieve()
			.body(new org.springframework.core.ParameterizedTypeReference<>() {
			});
		if (response == null) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>(response);
		if (excludeSystemVoices) {
			result.remove("system_voice");
		}
		return result;
	}

	@Override
	public void deleteVoice(String voiceId) {
		ensureApiKeyConfigured();
		Map<String, Object> response = restClient.post()
			.uri("/v1/voice/delete")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("voice_id", voiceId))
			.retrieve()
			.body(new org.springframework.core.ParameterizedTypeReference<>() {
			});
		assertBaseResponseOk(response, "MiniMax 音色删除失败");
	}

	private void assertBaseResponseOk(Map<String, Object> response, String message) {
		if (response == null) {
			throw new VoiceGenerationException(message);
		}
		Object baseResp = response.get("base_resp");
		if (baseResp instanceof Map<?, ?> map && Integer.valueOf(0).equals(map.get("status_code"))) {
			return;
		}
		throw new VoiceGenerationException(message);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static final class NamedByteArrayResource extends ByteArrayResource {
		private final String filename;

		private NamedByteArrayResource(byte[] byteArray, String filename) {
			super(byteArray);
			this.filename = StringUtils.hasText(filename) ? filename : "audio.mp3";
		}

		@Override
		public String getFilename() {
			return filename;
		}
	}
}

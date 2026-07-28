package com.recording.platform.review.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class DashScopeReviewAiProvider implements ReviewAiProvider {
	private final DashScopeSettings settings;
	private final ObjectMapper mapper;

	public DashScopeReviewAiProvider(DashScopeSettings settings, ObjectMapper mapper) {
		this.settings = settings;
		this.mapper = mapper;
	}

	@Override
	public void ensureConfigured() {
		if (!StringUtils.hasText(settings.apiKey())) {
			throw new ApiException(
				org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
				"REVIEW_AI_NOT_CONFIGURED",
				"AI 辅助审核尚未配置"
			);
		}
	}

	@Override
	public ReviewAiProviderResult generate(
		ReviewAiJobType type,
		ReviewAiStageConfig config,
		String sourceText,
		byte[] audio,
		String audioFormat
	) {
		ensureConfigured();
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(Math.min(config.timeoutMs(), 30000)));
		requestFactory.setReadTimeout(Duration.ofMillis(config.timeoutMs()));
		RestClient client = RestClient.builder()
			.baseUrl(settings.baseUrl())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.apiKey())
			.build();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", config.model());
		body.put("stream", true);
		body.put("modalities", List.of("text"));
		body.put("temperature", config.temperature());
		body.put("top_p", config.topP());
		body.put("max_tokens", config.maxTokens());
		body.put("messages", messages(type, config.prompt(), sourceText, audio, audioFormat));
		try {
			var response = client.post().uri("/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.body(body)
				.retrieve()
				.toEntity(String.class);
			String requestId = response.getHeaders().getFirst("x-request-id");
			return new ReviewAiProviderResult(parseSse(response.getBody()), requestId);
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().value() == 429) {
				throw new ReviewAiProviderException("REVIEW_AI_RATE_LIMITED", "AI 服务请求过于频繁");
			}
			if (exception.getStatusCode().is5xxServerError()) {
				throw new ReviewAiProviderException("REVIEW_AI_PROVIDER_UNAVAILABLE", "AI 服务暂时不可用");
			}
			throw new ReviewAiProviderException("REVIEW_AI_PROVIDER_ERROR", "AI 服务请求失败");
		} catch (ReviewAiProviderException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ReviewAiProviderException("REVIEW_AI_PROVIDER_ERROR", "AI 服务响应解析失败");
		}
	}

	private List<Map<String, Object>> messages(
		ReviewAiJobType type, String prompt, String sourceText, byte[] audio, String audioFormat
	) {
		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", prompt));
		if (type == ReviewAiJobType.TEXT_REFINE) {
			messages.add(Map.of("role", "user", "content", sourceText));
		} else {
			String encoded = Base64.getEncoder().encodeToString(audio);
			Map<String, Object> inputAudio = Map.of(
				"data", "data:audio/" + audioFormat + ";base64," + encoded,
				"format", audioFormat
			);
			messages.add(Map.of(
				"role", "user",
				"content", List.of(
					Map.of("type", "input_audio", "input_audio", inputAudio),
					Map.of("type", "text", "text", "请转写这段音频。")
				)
			));
		}
		return messages;
	}

	String parseSse(String body) {
		if (body == null) throw new ReviewAiProviderException("REVIEW_AI_EMPTY_RESULT", "AI 未返回结果");
		StringBuilder text = new StringBuilder();
		for (String line : body.split("\\R")) {
			if (!line.startsWith("data:")) continue;
			String data = line.substring(5).trim();
			if (data.isEmpty() || "[DONE]".equals(data)) continue;
			try {
				JsonNode root = mapper.readTree(data.getBytes(StandardCharsets.UTF_8));
				JsonNode content = root.path("choices").path(0).path("delta").path("content");
				if (content.isTextual()) text.append(content.asText());
			} catch (Exception exception) {
				throw new ReviewAiProviderException("REVIEW_AI_RESPONSE_INVALID", "AI 返回格式无法解析");
			}
		}
		String result = text.toString().trim();
		if (result.isEmpty()) throw new ReviewAiProviderException("REVIEW_AI_EMPTY_RESULT", "AI 未返回可用文字");
		return result;
	}
}

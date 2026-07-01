package com.recording.platform.voice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.voice.VoiceGenerationException;

public final class MiniMaxAudioParser {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private MiniMaxAudioParser() {
	}

	public static byte[] parseAudioBytes(String json) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(json);
			int statusCode = root.path("base_resp").path("status_code").asInt(-1);
			if (statusCode != 0) {
				throw new VoiceGenerationException("MiniMax 合成失败，状态码：" + statusCode);
			}
			String audioHex = root.path("data").path("audio").asText("");
			if (audioHex.isBlank()) {
				throw new VoiceGenerationException("MiniMax 响应缺少音频数据");
			}
			return hexToBytes(audioHex);
		} catch (VoiceGenerationException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new VoiceGenerationException("解析 MiniMax 音频响应失败");
		}
	}

	private static byte[] hexToBytes(String hex) {
		if (hex.length() % 2 != 0) {
			throw new VoiceGenerationException("MiniMax 音频数据格式异常");
		}
		byte[] bytes = new byte[hex.length() / 2];
		for (int index = 0; index < hex.length(); index += 2) {
			bytes[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
		}
		return bytes;
	}
}

package com.recording.platform.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IntegrationApiKeyAuthenticator {
	private final byte[] configuredHash;

	public IntegrationApiKeyAuthenticator(
		@Value("${recording.integration.api-key-sha256:}") String configuredHash
	) {
		String normalized = configuredHash == null ? "" : configuredHash.trim();
		if (normalized.isEmpty()) {
			this.configuredHash = null;
			return;
		}
		if (!normalized.matches("(?i)[0-9a-f]{64}")) {
			throw new IllegalStateException("RECORDING_INTEGRATION_API_KEY_SHA256 必须是 64 位 SHA-256 十六进制值");
		}
		this.configuredHash = HexFormat.of().parseHex(normalized);
	}

	public boolean isConfigured() {
		return configuredHash != null;
	}

	public boolean matches(String rawKey) {
		if (configuredHash == null || !StringUtils.hasText(rawKey)) {
			return false;
		}
		return MessageDigest.isEqual(configuredHash, sha256(rawKey));
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("运行环境不支持 SHA-256", exception);
		}
	}
}

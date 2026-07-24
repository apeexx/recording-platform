package com.recording.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class IntegrationApiKeyAuthenticatorTests {
	@Test
	void emptyHashLeavesIntegrationUnconfigured() {
		IntegrationApiKeyAuthenticator authenticator = new IntegrationApiKeyAuthenticator("");

		assertThat(authenticator.isConfigured()).isFalse();
		assertThat(authenticator.matches("any-key")).isFalse();
	}

	@Test
	void validHashMatchesOnlyTheOriginalKey() {
		String rawKey = "integration-test-key";
		IntegrationApiKeyAuthenticator authenticator = new IntegrationApiKeyAuthenticator(sha256(rawKey));

		assertThat(authenticator.isConfigured()).isTrue();
		assertThat(authenticator.matches(rawKey)).isTrue();
		assertThat(authenticator.matches("wrong-key")).isFalse();
		assertThat(authenticator.matches("")).isFalse();
	}

	@Test
	void malformedConfiguredHashFailsClosedWithoutEchoingTheValue() {
		String invalidHash = "secret-invalid-hash";

		assertThatThrownBy(() -> new IntegrationApiKeyAuthenticator(invalidHash))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("RECORDING_INTEGRATION_API_KEY_SHA256")
			.hasMessageNotContaining(invalidHash);
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}
}

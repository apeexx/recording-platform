package com.recording.platform.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class OpaqueTokenService {
	private final SecureRandom secureRandom = new SecureRandom();

	public TokenPair issue() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new TokenPair(raw, hash(raw));
	}

	public String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	public record TokenPair(String raw, String hash) {
	}
}

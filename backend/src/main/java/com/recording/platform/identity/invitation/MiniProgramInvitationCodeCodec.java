package com.recording.platform.identity.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class MiniProgramInvitationCodeCodec {
	private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int CODE_LENGTH = 12;
	private final SecureRandom random = new SecureRandom();

	public String generate() {
		char[] value = new char[CODE_LENGTH];
		for (int index = 0; index < value.length; index++) {
			value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
		}
		String normalized = new String(value);
		return normalized.substring(0, 4) + "-" + normalized.substring(4, 8) + "-" + normalized.substring(8);
	}

	public String normalize(String rawCode) {
		if (rawCode == null) return null;
		return rawCode.replace("-", "").replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
	}

	public String hash(String rawCode) {
		return digest(normalize(rawCode));
	}

	public String identityHash(String appId, String openId) {
		return digest(appId + "\0" + openId);
	}

	private String digest(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}
}

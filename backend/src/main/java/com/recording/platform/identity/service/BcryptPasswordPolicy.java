package com.recording.platform.identity.service;

import java.nio.charset.StandardCharsets;

public final class BcryptPasswordPolicy {
	private static final int MINIMUM_CHARACTERS = 8;
	private static final int MAXIMUM_UTF8_BYTES = 72;

	private BcryptPasswordPolicy() {
	}

	public static boolean isValidForEncoding(String password) {
		return password != null
			&& !password.isBlank()
			&& password.length() >= MINIMUM_CHARACTERS
			&& password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_UTF8_BYTES;
	}
}

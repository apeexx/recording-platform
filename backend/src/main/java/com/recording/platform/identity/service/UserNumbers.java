package com.recording.platform.identity.service;

import java.util.Locale;
import java.util.UUID;

final class UserNumbers {
	private UserNumbers() {
	}

	static String next() {
		return "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
	}
}

package com.recording.platform.review.ai;

final class ReviewAiProviderException extends RuntimeException {
	private final String code;

	ReviewAiProviderException(String code, String message) {
		super(message);
		this.code = code;
	}

	String code() {
		return code;
	}
}

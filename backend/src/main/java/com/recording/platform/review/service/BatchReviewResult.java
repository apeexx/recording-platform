package com.recording.platform.review.service;

public record BatchReviewResult(
	String itemId,
	boolean success,
	String code,
	String message,
	Long revision
) {
	public static BatchReviewResult success(String itemId, long revision) {
		return new BatchReviewResult(itemId, true, null, null, revision);
	}
	public static BatchReviewResult failure(String itemId, String code, String message) {
		return new BatchReviewResult(itemId, false, code, message, null);
	}
}

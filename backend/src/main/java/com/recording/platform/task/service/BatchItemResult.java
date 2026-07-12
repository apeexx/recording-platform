package com.recording.platform.task.service;

public record BatchItemResult(String itemId, boolean success, String code, String message, Long revision) {
	public static BatchItemResult success(String itemId, long revision) {
		return new BatchItemResult(itemId, true, null, null, revision);
	}
	public static BatchItemResult failure(String itemId, String code, String message) {
		return new BatchItemResult(itemId, false, code, message, null);
	}
}

package com.recording.platform.review.service;

public record BatchReviewCommand(String itemId, long expectedRevision, String text) { }

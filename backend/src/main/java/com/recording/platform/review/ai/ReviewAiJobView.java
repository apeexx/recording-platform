package com.recording.platform.review.ai;

public record ReviewAiJobView(
	String id,
	ReviewAiJobType type,
	ReviewAiJobStatus status,
	long itemRevision,
	String reviewAssignmentId,
	String resultText,
	String model,
	String requestId,
	Long durationMillis,
	String failureCode,
	String failureMessage
) {
	static ReviewAiJobView from(ReviewAiJob job) {
		return new ReviewAiJobView(
			job.getId(), job.getType(), job.getStatus(), job.getItemRevision(),
			job.getReviewAssignmentId(), job.getResultText(), job.getModel(),
			job.getRequestId(), job.getDurationMillis(), job.getFailureCode(), job.getFailureMessage()
		);
	}
}

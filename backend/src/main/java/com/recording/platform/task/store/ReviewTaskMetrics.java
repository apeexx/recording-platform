package com.recording.platform.task.store;

public record ReviewTaskMetrics(
	String taskId,
	long effectiveItemCount,
	long completedCount,
	long reviewEnteredCount,
	long reviewProcessedCount,
	long submittedCount,
	long reviewPendingCount,
	long todayCompletedCount
) {
	public long pendingCount() {
		return submittedCount + reviewPendingCount;
	}
}

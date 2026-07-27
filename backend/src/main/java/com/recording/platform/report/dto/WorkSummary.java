package com.recording.platform.report.dto;

public record WorkSummary(
	long cumulativeSubmissions,
	long cumulativeDurationMillis,
	long currentCompletedCount,
	long currentDurationMillis,
	long releaseCount,
	long discardCount,
	long submissionCount,
	long completedCount,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) {
	public WorkSummary(
		long cumulativeSubmissions,
		long cumulativeDurationMillis,
		long currentCompletedCount,
		long currentDurationMillis,
		long releaseCount,
		long discardCount
	) {
		this(
			cumulativeSubmissions, cumulativeDurationMillis, currentCompletedCount, currentDurationMillis,
			releaseCount, discardCount, cumulativeSubmissions, currentCompletedCount, currentDurationMillis, 0, 0
		);
	}
}

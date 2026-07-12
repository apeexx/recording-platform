package com.recording.platform.report.dto;

public record WorkSummary(
	long cumulativeSubmissions,
	long cumulativeDurationMillis,
	long currentCompletedCount,
	long currentDurationMillis,
	long releaseCount,
	long discardCount
) { }

package com.recording.platform.report.dto;

public record ReviewerSummary(
	long claimCount,
	long releaseCount,
	long approveCount,
	long rejectCount,
	long averageProcessingMillis
) { }

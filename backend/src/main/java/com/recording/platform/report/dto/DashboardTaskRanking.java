package com.recording.platform.report.dto;

public record DashboardTaskRanking(
	String taskId, String taskCode, String taskName,
	long submissionCount, long completedCount, long recordingDurationMillis
) { }

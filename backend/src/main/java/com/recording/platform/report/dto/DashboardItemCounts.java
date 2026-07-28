package com.recording.platform.report.dto;

public record DashboardItemCounts(
	long total,
	long available,
	long recordingPending,
	long reworkPending,
	long submitted,
	long reviewPending,
	long completed,
	long discarded
) { }

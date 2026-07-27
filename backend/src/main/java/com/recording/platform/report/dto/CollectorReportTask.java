package com.recording.platform.report.dto;

import java.time.Instant;

public record CollectorReportTask(
	String taskId,
	String taskCode,
	String taskName,
	Instant latestSubmittedAt
) { }

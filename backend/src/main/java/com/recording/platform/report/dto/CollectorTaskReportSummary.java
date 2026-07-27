package com.recording.platform.report.dto;

public record CollectorTaskReportSummary(
	long submissionCount,
	long completedCount,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) { }

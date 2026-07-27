package com.recording.platform.report.dto;

import java.time.LocalDate;

public record CollectorTaskDaySummary(
	LocalDate date,
	long submissionCount,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) { }

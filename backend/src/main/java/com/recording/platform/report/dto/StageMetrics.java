package com.recording.platform.report.dto;

public record StageMetrics(
	long count,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) {
	public static StageMetrics empty() {
		return new StageMetrics(0, 0, 0, 0);
	}
}

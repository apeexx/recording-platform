package com.recording.platform.report.dto;

public record CollectorRankingRow(
	String collectorId,
	String collectorName,
	long submissionCount,
	long completedCount,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) {
	public CollectorRankingRow withName(String name) {
		return new CollectorRankingRow(
			collectorId, name, submissionCount, completedCount, recordingDurationMillis,
			referenceAudioDurationMillis, referenceVideoDurationMillis
		);
	}
}

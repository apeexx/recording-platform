package com.recording.platform.report.dto;

import java.time.Instant;

public record CollectorRankingRow(
	String collectorId,
	String collectorName,
	StageMetrics submissions,
	StageMetrics completions,
	Instant firstSubmissionAt,
	Instant latestSubmissionAt,
	Integer peakSubmissionHour
) {
	public CollectorRankingRow(
		String collectorId,
		String collectorName,
		long submissionCount,
		long completedCount,
		long recordingDurationMillis,
		long referenceAudioDurationMillis,
		long referenceVideoDurationMillis
	) {
		this(
			collectorId, collectorName,
			new StageMetrics(
				submissionCount, recordingDurationMillis,
				referenceAudioDurationMillis, referenceVideoDurationMillis
			),
			new StageMetrics(
				completedCount, recordingDurationMillis,
				referenceAudioDurationMillis, referenceVideoDurationMillis
			),
			null, null, null
		);
	}

	public CollectorRankingRow withName(String name) {
		return new CollectorRankingRow(
			collectorId, name, submissions, completions,
			firstSubmissionAt, latestSubmissionAt, peakSubmissionHour
		);
	}
}

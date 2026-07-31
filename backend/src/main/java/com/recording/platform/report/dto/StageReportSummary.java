package com.recording.platform.report.dto;

import java.util.List;

public record StageReportSummary(
	StageMetrics submissions,
	StageMetrics completions,
	List<SubmissionHourBucket> submissionHourDistribution
) {
	public static StageReportSummary empty() {
		return new StageReportSummary(
			StageMetrics.empty(),
			StageMetrics.empty(),
			java.util.stream.IntStream.range(0, 24)
				.map(index -> (index + 4) % 24)
				.mapToObj(hour -> new SubmissionHourBucket(hour, 0))
				.toList()
		);
	}
}

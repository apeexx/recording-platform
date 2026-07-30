package com.recording.platform.report.dto;

import java.util.List;

public record StageReportSummary(
	StageMetrics submissions,
	StageMetrics completions,
	List<SubmissionHourBucket> submissionHourDistribution
) { }

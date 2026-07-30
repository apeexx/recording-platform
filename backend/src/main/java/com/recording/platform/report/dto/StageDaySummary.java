package com.recording.platform.report.dto;

import java.time.LocalDate;

public record StageDaySummary(
	LocalDate date,
	StageMetrics submissions,
	StageMetrics completions
) { }

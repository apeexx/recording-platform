package com.recording.platform.report.dto;

import java.util.List;

public record CollectorTaskReport(
	String taskId,
	String taskCode,
	String taskName,
	CollectorTaskReportSummary summary,
	List<CollectorTaskDaySummary> days,
	List<CollectorTaskReportItem> recentSubmissions,
	StageReportSummary stageSummary,
	List<StageDaySummary> stageDays
) {
	public CollectorTaskReport(
		String taskId,
		String taskCode,
		String taskName,
		CollectorTaskReportSummary summary,
		List<CollectorTaskDaySummary> days,
		List<CollectorTaskReportItem> recentSubmissions
	) {
		this(taskId, taskCode, taskName, summary, days, recentSubmissions, StageReportSummary.empty(), List.of());
	}

	public CollectorTaskReport withStages(StageReportSummary stages, List<StageDaySummary> stageDays) {
		return new CollectorTaskReport(
			taskId, taskCode, taskName, summary, days, recentSubmissions,
			stages, stageDays == null ? List.of() : List.copyOf(stageDays)
		);
	}
}

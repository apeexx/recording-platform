package com.recording.platform.report.dto;

import java.util.List;

public record AdminCollectorTaskReport(
	String taskId,
	String taskCode,
	String taskName,
	String collectorId,
	String collectorName,
	StageReportSummary summary,
	List<StageDaySummary> days
) {
	public AdminCollectorTaskReport withCollectorName(String name) {
		return new AdminCollectorTaskReport(
			taskId, taskCode, taskName, collectorId, name, summary, days
		);
	}
}

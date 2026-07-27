package com.recording.platform.report.dto;

import java.util.List;

public record CollectorTaskReport(
	String taskId,
	String taskCode,
	String taskName,
	CollectorTaskReportSummary summary,
	List<CollectorTaskDaySummary> days,
	List<CollectorTaskReportItem> recentSubmissions
) { }

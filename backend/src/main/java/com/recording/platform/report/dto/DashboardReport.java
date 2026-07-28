package com.recording.platform.report.dto;

import java.util.List;

public record DashboardReport(
	DashboardTaskCounts tasks,
	DashboardItemCounts items,
	long currentCollectorCount,
	long todayFirstSubmissionCount,
	List<DashboardTrendPoint> trend,
	List<DashboardTaskRanking> taskRanking
) { }

package com.recording.platform.report.dto;

import java.time.Instant;
import java.util.List;

public record DashboardReport(
	DashboardTaskCounts tasks,
	DashboardItemCounts items,
	long currentCollectorCount,
	long todayFirstSubmissionCount,
	List<DashboardTrendPoint> trend,
	List<DashboardTaskRanking> taskRanking,
	Instant generatedAt
) {
	public DashboardReport withGeneratedAt(Instant value) {
		return new DashboardReport(
			tasks, items, currentCollectorCount, todayFirstSubmissionCount, trend, taskRanking, value
		);
	}
}

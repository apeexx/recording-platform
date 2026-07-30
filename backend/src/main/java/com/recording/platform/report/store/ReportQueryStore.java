package com.recording.platform.report.store;

import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.CollectorReportTask;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import com.recording.platform.report.dto.CollectorRankingRow;
import com.recording.platform.report.dto.AdminCollectorReportItem;
import com.recording.platform.report.dto.AdminCollectorTaskReport;
import com.recording.platform.report.dto.StageReportSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportQueryStore {
	WorkSummary aggregateWork(String collectorId, String taskId);
	WorkSummary aggregateWork(String collectorId, String taskId, Instant fromInclusive, Instant toExclusive);
	Page<SubmissionView> findSubmissions(String collectorId, Pageable pageable);
	List<CollectorReportTask> findCollectorTasks(String collectorId);
	Optional<CollectorTaskReport> collectorTaskReport(String collectorId, String taskId);
	Optional<CollectorTaskReport> collectorTaskReport(String collectorId, String taskId, LocalDate date);
	Optional<CollectorTaskReport> collectorTaskReport(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive
	);
	Page<CollectorTaskReportItem> findCollectorTaskSubmissions(String collectorId, String taskId, Pageable pageable);
	Page<CollectorTaskReportItem> findCollectorTaskSubmissions(
		String collectorId, String taskId, LocalDate date, Pageable pageable
	);
	Page<CollectorTaskReportItem> findCollectorTaskSubmissions(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive, Pageable pageable
	);
	Page<CollectorRankingRow> findCollectorRankings(
		String taskId, Instant fromInclusive, Instant toExclusive, String sortField,
		org.springframework.data.domain.Sort.Direction sortDirection, Pageable pageable
	);
	StageReportSummary aggregateAdminStages(
		String taskId, String collectorId, Instant fromInclusive, Instant toExclusive
	);
	Optional<AdminCollectorTaskReport> adminCollectorTaskReport(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive
	);
	Page<AdminCollectorReportItem> findAdminCollectorTaskSubmissions(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive, Pageable pageable
	);
	Page<AdminCollectorReportItem> findAdminCollectorTaskCompletions(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive, Pageable pageable
	);
	com.recording.platform.report.dto.DashboardReport dashboard(LocalDate fromDate, LocalDate today);
}

package com.recording.platform.report.store;

import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.ReviewerSummary;
import com.recording.platform.report.dto.CollectorReportTask;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportQueryStore {
	WorkSummary aggregateWork(String collectorId, String taskId);
	Page<SubmissionView> findSubmissions(String collectorId, Pageable pageable);
	ReviewerSummary aggregateReviewer(String reviewerId);
	List<CollectorReportTask> findCollectorTasks(String collectorId);
	Optional<CollectorTaskReport> collectorTaskReport(String collectorId, String taskId);
	Page<CollectorTaskReportItem> findCollectorTaskSubmissions(String collectorId, String taskId, Pageable pageable);
}

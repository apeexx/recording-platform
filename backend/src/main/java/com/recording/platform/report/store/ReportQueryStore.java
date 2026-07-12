package com.recording.platform.report.store;

import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.ReviewerSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportQueryStore {
	WorkSummary aggregateWork(String collectorId, String taskId);
	Page<SubmissionView> findSubmissions(String collectorId, Pageable pageable);
	ReviewerSummary aggregateReviewer(String reviewerId);
}

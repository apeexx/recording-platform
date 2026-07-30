package com.recording.platform.report.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.CollectorReportTaskList;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import com.recording.platform.report.dto.AdminCollectorReportItem;
import com.recording.platform.report.dto.AdminCollectorTaskReport;
import com.recording.platform.report.dto.StageReportSummary;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
	private final ReportService reports;

	public ReportController(ReportService reports) { this.reports = reports; }

	@GetMapping("/dashboard")
	public com.recording.platform.report.dto.DashboardReport dashboard(
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.dashboard(actor);
	}

	@GetMapping("/tasks")
	public StageReportSummary task(
		@RequestParam String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.taskStages(taskId, fromDate, toDate, actor);
	}

	public WorkSummary task(String taskId, PlatformPrincipal actor) { return reports.task(taskId, actor); }

	@GetMapping("/collectors")
	public WorkSummary collector(
		@RequestParam String userId,
		@RequestParam(required = false) String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.collector(userId, taskId, fromDate, toDate, actor);
	}

	public WorkSummary collector(String userId, PlatformPrincipal actor) { return reports.collector(userId, actor); }

	@GetMapping("/tasks/{taskId}/collectors")
	public PageResponse<com.recording.platform.report.dto.CollectorRankingRow> taskCollectors(
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@RequestParam(defaultValue = "completionCount") String sortBy,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.taskCollectors(taskId, fromDate, toDate, sortBy, page, size, actor);
	}

	@GetMapping("/collectors/{collectorId}/tasks/{taskId}")
	public AdminCollectorTaskReport collectorTask(
		@org.springframework.web.bind.annotation.PathVariable String collectorId,
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.adminCollectorTask(collectorId, taskId, fromDate, toDate, actor);
	}

	@GetMapping("/collectors/{collectorId}/tasks/{taskId}/completions")
	public PageResponse<AdminCollectorReportItem> collectorTaskCompletions(
		@org.springframework.web.bind.annotation.PathVariable String collectorId,
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.collectorTaskCompletions(
			collectorId, taskId, fromDate, toDate, page, size, actor
		);
	}

	@GetMapping("/collectors/{collectorId}/tasks/{taskId}/submissions")
	public PageResponse<AdminCollectorReportItem> collectorTaskSubmissions(
		@org.springframework.web.bind.annotation.PathVariable String collectorId,
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.collectorTaskSubmissions(
			collectorId, taskId, fromDate, toDate, page, size, actor
		);
	}

	@GetMapping("/me")
	public Object me(@AuthenticationPrincipal PlatformPrincipal actor) { return reports.me(actor); }

	@GetMapping("/me/submissions")
	public PageResponse<SubmissionView> mySubmissions(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.mySubmissions(page, size, actor);
	}

	@GetMapping("/me/tasks")
	public CollectorReportTaskList myTasks(@AuthenticationPrincipal PlatformPrincipal actor) {
		return reports.myTasks(actor);
	}

	@GetMapping("/me/tasks/{taskId}")
	public CollectorTaskReport myTask(
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate date,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.myTask(taskId, date, actor);
	}

	public CollectorTaskReport myTask(String taskId, PlatformPrincipal actor) {
		return reports.myTask(taskId, actor);
	}

	@GetMapping("/me/tasks/{taskId}/submissions")
	public PageResponse<CollectorTaskReportItem> myTaskSubmissions(
		@org.springframework.web.bind.annotation.PathVariable String taskId,
		@RequestParam(required = false) LocalDate date,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return reports.myTaskSubmissions(taskId, date, page, size, actor);
	}

	public PageResponse<CollectorTaskReportItem> myTaskSubmissions(
		String taskId, int page, int size, PlatformPrincipal actor
	) {
		return reports.myTaskSubmissions(taskId, page, size, actor);
	}
}

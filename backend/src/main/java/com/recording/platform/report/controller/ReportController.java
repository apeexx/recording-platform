package com.recording.platform.report.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.report.dto.ReviewerSummary;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
	private final ReportService reports;

	public ReportController(ReportService reports) { this.reports = reports; }

	@GetMapping("/tasks")
	public WorkSummary task(@RequestParam String taskId, @AuthenticationPrincipal PlatformPrincipal actor) {
		return reports.task(taskId, actor);
	}

	@GetMapping("/collectors")
	public WorkSummary collector(@RequestParam String userId, @AuthenticationPrincipal PlatformPrincipal actor) {
		return reports.collector(userId, actor);
	}

	@GetMapping("/reviewers")
	public ReviewerSummary reviewer(@RequestParam String userId, @AuthenticationPrincipal PlatformPrincipal actor) {
		return reports.reviewer(userId, actor);
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
}

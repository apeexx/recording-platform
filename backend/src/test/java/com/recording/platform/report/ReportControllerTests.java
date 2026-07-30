package com.recording.platform.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.report.controller.ReportController;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.security.PlatformPrincipal;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportControllerTests {
	@Test
	void exposesTaskCollectorAndPersonalReports() {
		ReportService service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		PlatformPrincipal admin = admin();
		WorkSummary work = new WorkSummary(2, 3000, 1, 2000, 0, 0);
		when(service.task("task-1", admin)).thenReturn(work);
		when(service.collector("collector-1", admin)).thenReturn(work);
		when(service.me(admin)).thenReturn(work);
		PageResponse<SubmissionView> page = new PageResponse<>(List.of(), 0, 20, 0);
		when(service.mySubmissions(0, 20, admin)).thenReturn(page);

		assertThat(controller.task("task-1", admin)).isSameAs(work);
		assertThat(controller.collector("collector-1", admin)).isSameAs(work);
		assertThat(controller.me(admin)).isSameAs(work);
		assertThat(controller.mySubmissions(0, 20, admin)).isSameAs(page);
	}

	@Test
	void exposesAdminCollectorTaskDetailAndSubmissionPagination() {
		ReportService service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		PlatformPrincipal admin = admin();
		LocalDate from = LocalDate.of(2026, 7, 27);
		LocalDate to = LocalDate.of(2026, 7, 28);
		var report = new com.recording.platform.report.dto.AdminCollectorTaskReport(
			"task-1", "T000001", "普通话录音", "collector-1", "采集员",
			new com.recording.platform.report.dto.StageReportSummary(
				com.recording.platform.report.dto.StageMetrics.empty(),
				com.recording.platform.report.dto.StageMetrics.empty(),
				java.util.stream.IntStream.range(0, 24)
					.mapToObj(hour -> new com.recording.platform.report.dto.SubmissionHourBucket(hour, 0))
					.toList()
			),
			List.of()
		);
		PageResponse<com.recording.platform.report.dto.AdminCollectorReportItem> page =
			new PageResponse<>(List.of(), 0, 20, 0);
		when(service.adminCollectorTask("collector-1", "task-1", from, to, admin)).thenReturn(report);
		when(service.collectorTaskSubmissions(
			"collector-1", "task-1", from, to, 0, 20, admin
		)).thenReturn(page);

		assertThat(controller.collectorTask("collector-1", "task-1", from, to, admin))
			.isSameAs(report);
		assertThat(controller.collectorTaskSubmissions(
			"collector-1", "task-1", from, to, 0, 20, admin
		)).isSameAs(page);
	}

	@Test
	void exposesCollectorTaskDashboardAndSubmissionPagination() {
		ReportService service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		PlatformPrincipal collector = new PlatformPrincipal(
			"s", "collector-1", null, "采集员", UserRole.COLLECTOR, SessionType.MINIPROGRAM, false
		);
		var tasks = new com.recording.platform.report.dto.CollectorReportTaskList(List.of());
		var report = new com.recording.platform.report.dto.CollectorTaskReport(
			"task-1", "T000001", "普通话录音",
			new com.recording.platform.report.dto.CollectorTaskReportSummary(0, 0, 0, 0, 0),
			List.of(), List.of()
		);
		PageResponse<com.recording.platform.report.dto.CollectorTaskReportItem> page =
			new PageResponse<>(List.of(), 0, 20, 0);
		when(service.myTasks(collector)).thenReturn(tasks);
		when(service.myTask("task-1", collector)).thenReturn(report);
		when(service.myTaskSubmissions("task-1", 0, 20, collector)).thenReturn(page);

		assertThat(controller.myTasks(collector)).isSameAs(tasks);
		assertThat(controller.myTask("task-1", collector)).isSameAs(report);
		assertThat(controller.myTaskSubmissions("task-1", 0, 20, collector)).isSameAs(page);
	}

	@Test
	void exposesDateFilteredTaskReportAndCollectorRanking() {
		ReportService service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		PlatformPrincipal admin = admin();
		LocalDate date = LocalDate.of(2026, 7, 27);
		var work = new com.recording.platform.report.dto.StageReportSummary(
			new com.recording.platform.report.dto.StageMetrics(4, 0, 419_000, 420_000),
			new com.recording.platform.report.dto.StageMetrics(2, 0, 200_000, 210_000),
			List.of()
		);
		var ranking = new PageResponse<com.recording.platform.report.dto.CollectorRankingRow>(
			List.of(new com.recording.platform.report.dto.CollectorRankingRow(
				"MINI-1", "采集员一", 4, 2, 0, 419_000, 420_000
			)), 0, 20, 1
		);
		when(service.taskStages("task-1", date, date, admin)).thenReturn(work);
		when(service.taskCollectors("task-1", date, date, "completionCount", "asc", 0, 20, admin()))
			.thenReturn(ranking);

		assertThat(controller.task("task-1", date, date, admin)).isSameAs(work);
		assertThat(controller.taskCollectors(
			"task-1", date, date, "completionCount", "asc", 0, 20, admin
		)).isSameAs(ranking);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}
}

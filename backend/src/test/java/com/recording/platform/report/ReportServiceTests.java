package com.recording.platform.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.report.store.ReportQueryStore;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportServiceTests {
	@Test
	void dashboardUsesShanghaiTodayAndRejectsNonAdmins() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		Clock clock = Clock.fixed(
			Instant.parse("2026-07-27T19:00:00Z"), ZoneId.of("Asia/Shanghai")
		);
		var expected = new com.recording.platform.report.dto.DashboardReport(
			new com.recording.platform.report.dto.DashboardTaskCounts(4, 1, 1, 1, 1),
			new com.recording.platform.report.dto.DashboardItemCounts(12, 2, 2, 1, 1, 1, 4, 1),
			3, 2,
			List.of(new com.recording.platform.report.dto.DashboardTrendPoint(
				LocalDate.of(2026, 7, 27), 2, 8_000
			)),
			List.of(),
			Instant.EPOCH
		);
		when(queries.dashboard(
			LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 27)
		)).thenReturn(expected);
		ReportService service = new ReportService(items, queries, null, clock);

		assertThat(service.dashboard(admin()).generatedAt())
			.isEqualTo(Instant.parse("2026-07-27T19:00:00Z"));
		assertThatThrownBy(() -> service.dashboard(collector()))
			.isInstanceOfSatisfying(com.recording.platform.api.ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("ACCESS_DENIED"));
	}
	@Test
	void taskSummaryPassesInclusiveShanghaiDateRangeToMongoQuery() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		var expected = new com.recording.platform.report.dto.WorkSummary(
			2, 3_000, 1, 2_000, 0, 0, 2, 1, 2_000, 9_000, 4_000
		);
		Instant from = Instant.parse("2026-07-26T20:00:00Z");
		Instant to = Instant.parse("2026-07-27T20:00:00Z");
		when(queries.aggregateWork("collector-1", "task-1", from, to)).thenReturn(expected);
		ReportService service = new ReportService(items, queries);

		var result = service.collector(
			"collector-1", "task-1", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 27), admin()
		);

		assertThat(result).isEqualTo(expected);
	}

	@Test
	void rejectsAnInvertedReportDateRange() {
		ReportService service = new ReportService(mock(TaskItemStore.class), mock(ReportQueryStore.class));

		assertThatThrownBy(() -> service.task(
			"task-1", LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 27), admin()
		)).isInstanceOfSatisfying(com.recording.platform.api.ApiException.class,
			error -> assertThat(error.getCode()).isEqualTo("INVALID_REPORT_DATE_RANGE"));
	}

	@Test
	void taskCollectorRankingPassesAscendingDirectionToStore() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		Instant from = Instant.parse("2026-07-26T20:00:00Z");
		Instant to = Instant.parse("2026-07-27T20:00:00Z");
		when(queries.findCollectorRankings(
			"task-1", from, to, "submissionCount", Sort.Direction.ASC, PageRequest.of(0, 20)
		)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		ReportService service = new ReportService(items, queries);

		service.taskCollectors(
			"task-1", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 27),
			"submissionCount", "asc", 0, 20, admin()
		);

		verify(queries).findCollectorRankings(
			"task-1", from, to, "submissionCount", Sort.Direction.ASC, PageRequest.of(0, 20)
		);
	}

	@Test
	void taskCollectorRankingAcceptsEveryDocumentedSortField() {
		ReportQueryStore queries = mock(ReportQueryStore.class);
		ReportService service = new ReportService(mock(TaskItemStore.class), queries);
		PageRequest pageable = PageRequest.of(0, 20);
		List<String> fields = List.of(
			"submissionCount",
			"submissionRecordingDurationMillis",
			"submissionReferenceAudioDurationMillis",
			"submissionReferenceVideoDurationMillis",
			"completionCount",
			"completionRecordingDurationMillis",
			"completionReferenceAudioDurationMillis",
			"completionReferenceVideoDurationMillis",
			"firstSubmissionAt",
			"latestSubmissionAt"
		);

		for (String field : fields) {
			when(queries.findCollectorRankings(
				"task-1", null, null, field, Sort.Direction.DESC, pageable
			)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

			service.taskCollectors("task-1", null, null, field, "desc", 0, 20, admin());

			verify(queries).findCollectorRankings(
				"task-1", null, null, field, Sort.Direction.DESC, pageable
			);
		}
	}

	@Test
	void taskCollectorRankingRejectsUnknownSortDirection() {
		ReportService service = new ReportService(mock(TaskItemStore.class), mock(ReportQueryStore.class));

		assertThatThrownBy(() -> service.taskCollectors(
			"task-1", null, null, "completionCount", "sideways", 0, 20, admin()
		)).isInstanceOfSatisfying(com.recording.platform.api.ApiException.class, error -> {
			assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
			assertThat(error.getCode()).isEqualTo("INVALID_REPORT_SORT_DIRECTION");
		});
	}

	@Test
	void collectorSummarySeparatesCumulativeReworkFromCurrentValidResults() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem completed = item("item-1", TaskItemStatus.COMPLETED);
		completed.setSubmissions(List.of(submission(1000), submission(2000)));
		completed.setCurrentResult(new TaskItemResult(recording(2000), "文本"));
		TaskItem released = item("item-2", TaskItemStatus.AVAILABLE);
		released.setSubmissions(List.of(submission(3000)));
		released.setOperations(List.of(operation("RELEASE", "collector-1", "2026-07-12T08:00:00Z")));
		TaskItem discarded = item("item-3", TaskItemStatus.DISCARDED);
		discarded.setSubmissions(List.of(submission(4000)));
		discarded.setCurrentResult(new TaskItemResult(recording(4000), null));
		discarded.setOperations(List.of(
			operation("ADMIN_DISCARD", "admin-1", "2026-07-12T09:00:00Z"),
			operation("COLLECTOR_DISCARD", "collector-1", "2026-07-12T10:00:00Z")
		));
		when(items.findForReport("collector-1", null)).thenReturn(List.of(completed, released, discarded));
		ReportService service = new ReportService(items);

		var summary = service.collector("collector-1", admin());

		assertThat(summary.cumulativeSubmissions()).isEqualTo(4);
		assertThat(summary.cumulativeDurationMillis()).isEqualTo(10_000);
		assertThat(summary.currentCompletedCount()).isEqualTo(1);
		assertThat(summary.currentDurationMillis()).isEqualTo(2_000);
		assertThat(summary.releaseCount()).isEqualTo(1);
		assertThat(summary.discardCount()).isEqualTo(2);
	}

	@Test
	void collectorSubmissionHistoryKeepsEveryReworkAsASeparateRow() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item("item-1", TaskItemStatus.COMPLETED);
		SubmissionHistory first = submission(1000);
		first.setOperationId("submit-1");
		first.setSubmittedAt(Instant.parse("2026-07-12T08:00:00Z"));
		SubmissionHistory second = submission(2000);
		second.setOperationId("submit-2");
		second.setSubmittedAt(Instant.parse("2026-07-12T09:00:00Z"));
		item.setSubmissions(List.of(first, second));
		when(items.findForReport("collector-1", null)).thenReturn(List.of(item));
		ReportService service = new ReportService(items);

		var page = service.mySubmissions(0, 20, collector());

		assertThat(page.total()).isEqualTo(2);
		assertThat(page.items()).extracting((row) -> row.operationId())
			.containsExactly("submit-2", "submit-1");
	}

	@Test
	void productionQueriesDelegateWorkAndSubmissionPaginationToMongoStore() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		var expected = new com.recording.platform.report.dto.WorkSummary(4, 10_000, 1, 2_000, 1, 1);
		when(queries.aggregateWork("collector-1", null)).thenReturn(expected);
		var row = new com.recording.platform.report.dto.SubmissionView(
			"item-1", "task-1", "submit-1", Instant.parse("2026-07-12T08:00:00Z"),
			1000L, false, true, null, TaskItemStatus.COMPLETED
		);
		when(queries.findSubmissions("collector-1", PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
		ReportService service = new ReportService(items, queries);

		assertThat(service.collector("collector-1", admin())).isEqualTo(expected);
		assertThat(service.mySubmissions(0, 20, collector()).items()).containsExactly(row);
	}

	@Test
	void collectorTaskDashboardUsesOneCurrentAssignmentRowPerItem() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		var task = new com.recording.platform.report.dto.CollectorReportTask(
			"task-1", "T000001", "普通话录音", Instant.parse("2026-07-27T07:20:00Z")
		);
		var summary = new com.recording.platform.report.dto.CollectorTaskReportSummary(
			2, 1, 12_000, 30_000, 8_000
		);
		var day = new com.recording.platform.report.dto.CollectorTaskDaySummary(
			LocalDate.of(2026, 7, 27), 2, 1, 12_000, 30_000, 8_000
		);
		var row = new com.recording.platform.report.dto.CollectorTaskReportItem(
			"item-1", "T000001-0000001",
			Instant.parse("2026-07-27T06:00:00Z"), Instant.parse("2026-07-27T07:20:00Z"),
			TaskItemStatus.COMPLETED, 12_000, 15_000, 4_000, true, true
		);
		var report = new com.recording.platform.report.dto.CollectorTaskReport(
			"task-1", "T000001", "普通话录音", summary, List.of(day), List.of(row)
		);
		when(queries.findCollectorTasks("collector-1")).thenReturn(List.of(task));
		when(queries.collectorTaskReport("collector-1", "task-1")).thenReturn(Optional.of(report));
		when(queries.findCollectorTaskSubmissions("collector-1", "task-1", PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
		ReportService service = new ReportService(items, queries);

		assertThat(service.myTasks(collector()).items()).containsExactly(task);
		assertThat(service.myTask("task-1", collector())).isEqualTo(report);
		assertThat(service.myTaskSubmissions("task-1", 0, 20, collector()).items()).containsExactly(row);
	}

	@Test
	void adminCollectorTaskDetailUsesInclusiveShanghaiDateRange() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		Instant from = Instant.parse("2026-07-26T20:00:00Z");
		Instant to = Instant.parse("2026-07-28T20:00:00Z");
		var report = new com.recording.platform.report.dto.CollectorTaskReport(
			"task-1", "T000001", "普通话录音",
			new com.recording.platform.report.dto.CollectorTaskReportSummary(2, 1, 12_000, 0, 30_000),
			List.of(), List.of()
		);
		var row = new com.recording.platform.report.dto.AdminCollectorReportItem(
			"item-1", "T000001-0000001", from, from, from, TaskItemStatus.COMPLETED,
			12_000, 0, 30_000, false, true
		);
		when(queries.collectorTaskReport("collector-1", "task-1", from, to))
			.thenReturn(Optional.of(report));
		when(queries.findAdminCollectorTaskSubmissions(
			"collector-1", "task-1", from, to, PageRequest.of(0, 20)
		)).thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));
		ReportService service = new ReportService(items, queries);

		assertThat(service.collectorTask(
			"collector-1", "task-1",
			LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28), admin()
		)).isEqualTo(report);
		assertThat(service.collectorTaskSubmissions(
			"collector-1", "task-1",
			LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28),
			0, 20, admin()
		).items()).containsExactly(row);
	}

	@Test
	void adminReportsExposeIndependentSubmissionAndCompletionStages() {
		TaskItemStore items = mock(TaskItemStore.class);
		ReportQueryStore queries = mock(ReportQueryStore.class);
		Instant from = Instant.parse("2026-07-26T20:00:00Z");
		Instant to = Instant.parse("2026-07-28T20:00:00Z");
		var submissions = new com.recording.platform.report.dto.StageMetrics(3, 12_000, 30_000, 8_000);
		var completions = new com.recording.platform.report.dto.StageMetrics(2, 9_000, 20_000, 4_000);
		var summary = new com.recording.platform.report.dto.StageReportSummary(
			submissions, completions,
			java.util.stream.IntStream.range(0, 24)
				.map(index -> (index + 4) % 24)
				.mapToObj(hour -> new com.recording.platform.report.dto.SubmissionHourBucket(
					hour, hour == 9 ? 2 : 0
				)).toList()
		);
		var completionRow = new com.recording.platform.report.dto.AdminCollectorReportItem(
			"item-1", "T000001-0000001", from, from, Instant.parse("2026-07-27T01:00:00Z"),
			TaskItemStatus.COMPLETED, 9_000, 10_000, 2_000, true, true
		);
		when(queries.aggregateAdminStages("task-1", null, from, to)).thenReturn(summary);
		when(queries.findAdminCollectorTaskCompletions(
			"collector-1", "task-1", from, to, PageRequest.of(0, 20)
		)).thenReturn(new PageImpl<>(List.of(completionRow), PageRequest.of(0, 20), 1));
		ReportService service = new ReportService(items, queries);

		assertThat(service.taskStages(
			"task-1", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28), admin()
		)).isEqualTo(summary);
		assertThat(service.collectorTaskCompletions(
			"collector-1", "task-1", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 28),
			0, 20, admin()
		).items()).containsExactly(completionRow);
	}

	@Test
	void personalReportRejectsReviewerAfterReviewerStatisticsRemoval() {
		ReportService service = new ReportService(mock(TaskItemStore.class), mock(ReportQueryStore.class));
		PlatformPrincipal reviewer = new PlatformPrincipal(
			"s", "reviewer-1", "reviewer", "审核员", UserRole.REVIEWER, SessionType.WEB, false
		);

		assertThatThrownBy(() -> service.me(reviewer))
			.isInstanceOfSatisfying(com.recording.platform.api.ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("ACCESS_DENIED"));
	}

	private TaskItem item(String id, TaskItemStatus status) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId("task-1");
		item.setCollectorId("collector-1");
		item.setStatus(status);
		return item;
	}

	private SubmissionHistory submission(long duration) {
		SubmissionHistory history = new SubmissionHistory();
		history.setDurationMillis(duration);
		history.setSubmittedAt(Instant.parse("2026-07-12T08:00:00Z"));
		return history;
	}

	private SubmittedRecording recording(long duration) {
		return new SubmittedRecording("m", "r.wav", RecordingFormat.WAV, 1, 16000, 1, duration);
	}

	private OperationHistory operation(String type, String actor, String time) {
		OperationHistory operation = new OperationHistory();
		operation.setType(type);
		operation.setActorUserId(actor);
		operation.setOccurredAt(Instant.parse(time));
		return operation;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	private PlatformPrincipal collector() {
		return new PlatformPrincipal(
			"s", "collector-1", null, "采集员", UserRole.COLLECTOR, SessionType.MINIPROGRAM, false
		);
	}
}

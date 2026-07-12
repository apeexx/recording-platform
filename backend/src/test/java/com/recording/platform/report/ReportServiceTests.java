package com.recording.platform.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceTests {
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
		discarded.setOperations(List.of(operation("ADMIN_DISCARD", "admin-1", "2026-07-12T09:00:00Z")));
		when(items.findForReport("collector-1", null)).thenReturn(List.of(completed, released, discarded));
		ReportService service = new ReportService(items);

		var summary = service.collector("collector-1", admin());

		assertThat(summary.cumulativeSubmissions()).isEqualTo(4);
		assertThat(summary.cumulativeDurationMillis()).isEqualTo(10_000);
		assertThat(summary.currentCompletedCount()).isEqualTo(1);
		assertThat(summary.currentDurationMillis()).isEqualTo(2_000);
		assertThat(summary.releaseCount()).isEqualTo(1);
		assertThat(summary.discardCount()).isEqualTo(1);
	}

	@Test
	void reviewerSummaryCountsActionsAndAverageClaimToDecisionDuration() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item("item-1", TaskItemStatus.COMPLETED);
		item.setOperations(List.of(
			operation("REVIEW_CLAIM", "reviewer-1", "2026-07-12T08:00:00Z"),
			operation("REVIEW_APPROVE", "reviewer-1", "2026-07-12T08:02:00Z"),
			operation("REVIEW_CLAIM", "reviewer-1", "2026-07-12T09:00:00Z"),
			operation("REVIEW_REJECT", "reviewer-1", "2026-07-12T09:04:00Z"),
			operation("REVIEW_RELEASE", "reviewer-1", "2026-07-12T10:00:00Z")
		));
		when(items.findForReport(null, null)).thenReturn(List.of(item));
		ReportService service = new ReportService(items);

		var summary = service.reviewer("reviewer-1", admin());

		assertThat(summary.claimCount()).isEqualTo(2);
		assertThat(summary.releaseCount()).isEqualTo(1);
		assertThat(summary.approveCount()).isEqualTo(1);
		assertThat(summary.rejectCount()).isEqualTo(1);
		assertThat(summary.averageProcessingMillis()).isEqualTo(180_000);
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

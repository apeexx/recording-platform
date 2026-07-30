package com.recording.platform.report.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.CollectorReportTaskList;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import com.recording.platform.report.dto.CollectorRankingRow;
import com.recording.platform.report.dto.AdminCollectorReportItem;
import com.recording.platform.report.dto.AdminCollectorTaskReport;
import com.recording.platform.report.dto.StageReportSummary;
import com.recording.platform.api.PageResponse;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.recording.platform.report.store.ReportQueryStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.model.IdentityUser;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.ZoneId;

@Service
public class ReportService {
	private final TaskItemStore items;
	private final ReportQueryStore queries;
	private final IdentityDirectory users;
	private final Clock clock;

	public ReportService(TaskItemStore items) { this(items, null, null, Clock.system(ZoneId.of("Asia/Shanghai"))); }

	public ReportService(TaskItemStore items, ReportQueryStore queries) {
		this(items, queries, null, Clock.system(ZoneId.of("Asia/Shanghai")));
	}

	@Autowired
	public ReportService(TaskItemStore items, ReportQueryStore queries, IdentityDirectory users) {
		this(items, queries, users, Clock.system(ZoneId.of("Asia/Shanghai")));
	}

	public ReportService(TaskItemStore items, ReportQueryStore queries, IdentityDirectory users, Clock clock) {
		this.items = items;
		this.queries = queries;
		this.users = users;
		this.clock = clock;
	}

	public com.recording.platform.report.dto.DashboardReport dashboard(PlatformPrincipal actor) {
		requireAdmin(actor);
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
		return queries.dashboard(today.minusDays(6), today).withGeneratedAt(Instant.now(clock));
	}

	public WorkSummary collector(String collectorId, PlatformPrincipal actor) {
		requireAdminOrSelfCollector(collectorId, actor);
		return queries == null ? work(items.findForReport(collectorId, null)) : queries.aggregateWork(collectorId, null);
	}

	public WorkSummary collector(
		String collectorId, String taskId, LocalDate fromDate, LocalDate toDate, PlatformPrincipal actor
	) {
		requireAdminOrSelfCollector(collectorId, actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		return queries.aggregateWork(collectorId, blankToNull(taskId), range.fromInclusive(), range.toExclusive());
	}

	public WorkSummary task(String taskId, PlatformPrincipal actor) {
		requireAdmin(actor);
		return queries == null ? work(items.findForReport(null, taskId)) : queries.aggregateWork(null, taskId);
	}

	public WorkSummary task(String taskId, LocalDate fromDate, LocalDate toDate, PlatformPrincipal actor) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		return queries.aggregateWork(null, taskId, range.fromInclusive(), range.toExclusive());
	}

	public StageReportSummary taskStages(
		String taskId, LocalDate fromDate, LocalDate toDate, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		return queries.aggregateAdminStages(taskId, null, range.fromInclusive(), range.toExclusive());
	}

	public PageResponse<CollectorRankingRow> taskCollectors(
		String taskId, LocalDate fromDate, LocalDate toDate, String sortBy, String sortDirection,
		int page, int size, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		org.springframework.data.domain.Sort.Direction direction;
		try {
			direction = org.springframework.data.domain.Sort.Direction.fromString(sortDirection);
		} catch (IllegalArgumentException exception) {
			throw new com.recording.platform.api.ApiException(
				org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_REPORT_SORT_DIRECTION",
				"统计排序方向只支持 asc 或 desc"
			);
		}
		String sortField = switch (sortBy == null ? "" : sortBy) {
			case "submissionCount",
				"submissionRecordingDurationMillis",
				"submissionReferenceAudioDurationMillis",
				"submissionReferenceVideoDurationMillis",
				"completionCount",
				"completionRecordingDurationMillis",
				"completionReferenceAudioDurationMillis",
				"completionReferenceVideoDurationMillis",
				"firstSubmissionAt",
				"latestSubmissionAt" -> sortBy;
			default -> "completionCount";
		};
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		var result = queries.findCollectorRankings(
			taskId, range.fromInclusive(), range.toExclusive(), sortField, direction,
			PageRequest.of(safePage, safeSize)
		);
		Map<String, IdentityUser> identities = users == null ? Map.of() : users.findAllByIdIn(
			result.getContent().stream().map(CollectorRankingRow::collectorId).toList()
		).stream().collect(Collectors.toMap(IdentityUser::id, Function.identity()));
		List<CollectorRankingRow> rows = result.getContent().stream()
			.map(row -> row.withName(
				identities.containsKey(row.collectorId()) ? identities.get(row.collectorId()).name() : null
			)).toList();
		return new PageResponse<>(rows, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public Object me(PlatformPrincipal actor) {
		if (actor == null) throw forbidden();
		return switch (actor.role()) {
			case COLLECTOR -> collector(actor.userId(), actor);
			case REVIEWER -> throw forbidden();
			case ADMIN -> queries == null ? work(items.findForReport(null, null)) : queries.aggregateWork(null, null);
		};
	}

	public CollectorTaskReport collectorTask(
		String collectorId, String taskId, LocalDate fromDate, LocalDate toDate, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		return queries.collectorTaskReport(
			collectorId, taskId, range.fromInclusive(), range.toExclusive()
		).orElseThrow(() -> new ApiException(
			HttpStatus.NOT_FOUND, "REPORT_TASK_NOT_FOUND", "没有可统计的任务数据"
		));
	}

	public AdminCollectorTaskReport adminCollectorTask(
		String collectorId, String taskId, LocalDate fromDate, LocalDate toDate, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		AdminCollectorTaskReport report = queries.adminCollectorTaskReport(
			collectorId, taskId, range.fromInclusive(), range.toExclusive()
		).orElseThrow(() -> new ApiException(
			HttpStatus.NOT_FOUND, "REPORT_TASK_NOT_FOUND", "没有可统计的任务数据"
		));
		IdentityUser identity = users == null ? null : users.findById(collectorId).orElse(null);
		return report.withCollectorName(identity == null ? null : identity.name());
	}

	public PageResponse<AdminCollectorReportItem> collectorTaskCompletions(
		String collectorId, String taskId, LocalDate fromDate, LocalDate toDate,
		int page, int size, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		var result = queries.findAdminCollectorTaskCompletions(
			collectorId, taskId, range.fromInclusive(), range.toExclusive(),
			PageRequest.of(safePage, safeSize)
		);
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PageResponse<AdminCollectorReportItem> collectorTaskSubmissions(
		String collectorId, String taskId, LocalDate fromDate, LocalDate toDate,
		int page, int size, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		var result = queries.findAdminCollectorTaskSubmissions(
			collectorId, taskId, range.fromInclusive(), range.toExclusive(),
			PageRequest.of(safePage, safeSize)
		);
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PageResponse<SubmissionView> mySubmissions(int page, int size, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.COLLECTOR) throw forbidden();
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		if (queries != null) {
			var result = queries.findSubmissions(actor.userId(), PageRequest.of(safePage, safeSize));
			return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
		}
		List<SubmissionView> rows = new ArrayList<>();
		for (TaskItem item : items.findForReport(actor.userId(), null)) {
			for (SubmissionHistory submission : item.getSubmissions() == null ? List.<SubmissionHistory>of() : item.getSubmissions()) {
				rows.add(new SubmissionView(
					item.getId(), item.getTaskId(), submission.getOperationId(), submission.getSubmittedAt(),
					submission.getDurationMillis(), submission.isTextPresent(), submission.isAudioPresent(),
					submission.getReviewConclusion(), item.getStatus()
				));
			}
		}
		rows.sort(Comparator.comparing(SubmissionView::submittedAt,
			Comparator.nullsLast(Comparator.naturalOrder())).reversed());
		int from = Math.min(safePage * safeSize, rows.size());
		int to = Math.min(from + safeSize, rows.size());
		return new PageResponse<>(List.copyOf(rows.subList(from, to)), safePage, safeSize, rows.size());
	}

	public CollectorReportTaskList myTasks(PlatformPrincipal actor) {
		requireCollector(actor);
		return new CollectorReportTaskList(List.copyOf(queries.findCollectorTasks(actor.userId())));
	}

	public CollectorTaskReport myTask(String taskId, PlatformPrincipal actor) {
		requireCollector(actor);
		return queries.collectorTaskReport(actor.userId(), taskId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "REPORT_TASK_NOT_FOUND", "没有可统计的任务数据"
			));
	}

	public CollectorTaskReport myTask(String taskId, LocalDate date, PlatformPrincipal actor) {
		requireCollector(actor);
		return queries.collectorTaskReport(actor.userId(), taskId, date)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "REPORT_TASK_NOT_FOUND", "没有可统计的任务数据"
			));
	}

	public PageResponse<CollectorTaskReportItem> myTaskSubmissions(
		String taskId, int page, int size, PlatformPrincipal actor
	) {
		requireCollector(actor);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		var result = queries.findCollectorTaskSubmissions(
			actor.userId(), taskId, PageRequest.of(safePage, safeSize)
		);
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PageResponse<CollectorTaskReportItem> myTaskSubmissions(
		String taskId, LocalDate date, int page, int size, PlatformPrincipal actor
	) {
		requireCollector(actor);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		var result = queries.findCollectorTaskSubmissions(
			actor.userId(), taskId, date, PageRequest.of(safePage, safeSize)
		);
		return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
	}

	private WorkSummary work(List<TaskItem> values) {
		long submissions = 0, cumulativeDuration = 0, completed = 0, currentDuration = 0, releases = 0, discards = 0;
		for (TaskItem item : values) {
			for (SubmissionHistory submission : item.getSubmissions() == null ? List.<SubmissionHistory>of() : item.getSubmissions()) {
				submissions++;
				if (submission.getDurationMillis() != null) cumulativeDuration += submission.getDurationMillis();
			}
			if (item.getStatus() == TaskItemStatus.COMPLETED) {
				completed++;
				if (item.getCurrentResult() != null && item.getCurrentResult().audio() != null) {
					currentDuration += item.getCurrentResult().audio().durationMillis();
				}
			}
			for (OperationHistory operation : item.getOperations() == null ? List.<OperationHistory>of() : item.getOperations()) {
				if ("RELEASE".equals(operation.getType())) releases++;
				if ("ADMIN_DISCARD".equals(operation.getType())
					|| "COLLECTOR_DISCARD".equals(operation.getType())) discards++;
			}
		}
		return new WorkSummary(submissions, cumulativeDuration, completed, currentDuration, releases, discards);
	}

	private void requireAdminOrSelfCollector(String collectorId, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN
			&& (actor.role() != UserRole.COLLECTOR || !actor.userId().equals(collectorId))) throw forbidden();
	}
	private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private void requireCollector(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.COLLECTOR || queries == null) throw forbidden();
	}
	private void requireAdmin(PlatformPrincipal actor) { if (actor == null || actor.role() != UserRole.ADMIN) throw forbidden(); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限查看该统计"); }
}

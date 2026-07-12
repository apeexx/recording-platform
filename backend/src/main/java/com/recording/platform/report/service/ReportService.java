package com.recording.platform.report.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.report.dto.ReviewerSummary;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.api.PageResponse;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.recording.platform.report.store.ReportQueryStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReportService {
	private final TaskItemStore items;
	private final ReportQueryStore queries;

	public ReportService(TaskItemStore items) { this(items, null); }

	@Autowired
	public ReportService(TaskItemStore items, ReportQueryStore queries) {
		this.items = items;
		this.queries = queries;
	}

	public WorkSummary collector(String collectorId, PlatformPrincipal actor) {
		requireAdminOrSelfCollector(collectorId, actor);
		return queries == null ? work(items.findForReport(collectorId, null)) : queries.aggregateWork(collectorId, null);
	}

	public WorkSummary task(String taskId, PlatformPrincipal actor) {
		requireAdmin(actor);
		return queries == null ? work(items.findForReport(null, taskId)) : queries.aggregateWork(null, taskId);
	}

	public ReviewerSummary reviewer(String reviewerId, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN
			&& (actor.role() != UserRole.REVIEWER || !actor.userId().equals(reviewerId))) throw forbidden();
		if (queries != null) return queries.aggregateReviewer(reviewerId);
		long claims = 0, releases = 0, approvals = 0, rejections = 0, totalMillis = 0, decisions = 0;
		for (TaskItem item : items.findForReport(null, null)) {
			List<OperationHistory> operations = new ArrayList<>(item.getOperations() == null ? List.of() : item.getOperations());
			operations.sort(Comparator.comparing(OperationHistory::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
			ArrayDeque<Instant> claimedAt = new ArrayDeque<>();
			for (OperationHistory operation : operations) {
				if (!reviewerId.equals(operation.getActorUserId())) continue;
				switch (operation.getType()) {
					case "REVIEW_CLAIM" -> { claims++; if (operation.getOccurredAt() != null) claimedAt.add(operation.getOccurredAt()); }
					case "REVIEW_RELEASE" -> releases++;
					case "REVIEW_APPROVE", "REVIEW_REJECT" -> {
						if ("REVIEW_APPROVE".equals(operation.getType())) approvals++; else rejections++;
						if (!claimedAt.isEmpty() && operation.getOccurredAt() != null) {
							totalMillis += Math.max(0, Duration.between(claimedAt.removeFirst(), operation.getOccurredAt()).toMillis());
							decisions++;
						}
					}
					default -> { }
				}
			}
		}
		return new ReviewerSummary(claims, releases, approvals, rejections, decisions == 0 ? 0 : totalMillis / decisions);
	}

	public Object me(PlatformPrincipal actor) {
		if (actor == null) throw forbidden();
		return switch (actor.role()) {
			case COLLECTOR -> collector(actor.userId(), actor);
			case REVIEWER -> reviewer(actor.userId(), actor);
			case ADMIN -> queries == null ? work(items.findForReport(null, null)) : queries.aggregateWork(null, null);
		};
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
				if ("ADMIN_DISCARD".equals(operation.getType())) discards++;
			}
		}
		return new WorkSummary(submissions, cumulativeDuration, completed, currentDuration, releases, discards);
	}

	private void requireAdminOrSelfCollector(String collectorId, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN
			&& (actor.role() != UserRole.COLLECTOR || !actor.userId().equals(collectorId))) throw forbidden();
	}
	private void requireAdmin(PlatformPrincipal actor) { if (actor == null || actor.role() != UserRole.ADMIN) throw forbidden(); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限查看该统计"); }
}

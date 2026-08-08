package com.recording.platform.batch.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.batch.model.BatchOperationAction;
import com.recording.platform.batch.model.BatchOperationFailure;
import com.recording.platform.batch.model.BatchOperationJob;
import com.recording.platform.batch.model.BatchOperationJobStatus;
import com.recording.platform.batch.model.BatchOperationSnapshot;
import com.recording.platform.batch.model.BatchOperationSource;
import com.recording.platform.batch.store.BatchOperationJobStore;
import com.recording.platform.batch.store.BatchOperationSnapshotStore;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.importing.TaskItemActionService;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.task.service.TaskItemCollectorAssignmentService;
import com.recording.platform.task.store.TaskItemStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class BatchOperationService {
	private static final int SNAPSHOT_PAGE_SIZE = 500;
	private static final int MAX_FAILURES = 1_000;
	private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
	private final TaskItemStore items;
	private final BatchOperationJobStore jobs;
	private final BatchOperationSnapshotStore snapshots;
	private final TaskItemAdministrationService administration;
	private final TaskItemActionService actions;
	private final ReviewService reviews;
	private final TaskItemCollectorAssignmentService assignments;
	private final TaskExecutor executor;
	private final Clock clock;

	public BatchOperationService(
		TaskItemStore items,
		BatchOperationJobStore jobs,
		BatchOperationSnapshotStore snapshots,
		TaskItemAdministrationService administration,
		TaskItemActionService actions,
		ReviewService reviews,
		TaskItemCollectorAssignmentService assignments,
		@Qualifier("batchOperationTaskExecutor") TaskExecutor executor,
		Clock clock
	) {
		this.items = items;
		this.jobs = jobs;
		this.snapshots = snapshots;
		this.administration = administration;
		this.actions = actions;
		this.reviews = reviews;
		this.assignments = assignments;
		this.executor = executor;
		this.clock = clock;
	}

	public BatchOperationPreview preview(BatchOperationSelection selection, PlatformPrincipal actor) {
		requireSelectionAccess(selection, actor);
		List<TaskItem> selected = selectedItems(selection, actor);
		Map<BatchOperationAction, Long> counts = new EnumMap<>(BatchOperationAction.class);
		for (BatchOperationAction action : BatchOperationAction.values()) {
			counts.put(action, selected.stream().filter(item -> applicable(action, item.getStatus())).count());
		}
		return new BatchOperationPreview(selected.size(), counts);
	}

	public BatchOperationJob create(BatchOperationCommand command, PlatformPrincipal actor) {
		requireCommand(command, actor);
		String operationId = required(command.operationId());
		BatchOperationJob existing = jobs.findByActorUserIdAndOperationId(actor.userId(), operationId).orElse(null);
		if (existing != null) return existing;
		if (command.action() == BatchOperationAction.COLLECTOR_ASSIGN) {
			assignments.validateTarget(command.selection().taskId(), command.collectorId(), actor);
		}
		List<TaskItem> selected = selectedItems(command.selection(), actor);
		if (selected.isEmpty()) throw invalid("EMPTY_BATCH_SELECTION", "当前筛选结果没有可处理数据");
		Instant now = Instant.now(clock);
		BatchOperationJob job = new BatchOperationJob();
		job.setId(UUID.randomUUID().toString());
		job.setOperationId(operationId);
		job.setTaskId(command.selection().taskId());
		job.setSource(command.selection().source());
		job.setAction(command.action());
		job.setTargetStatus(command.targetStatus());
		job.setCollectorId(trimToNull(command.collectorId()));
		job.setReviewerId(trimToNull(command.reviewerId()));
		job.setActorUserId(actor.userId());
		job.setActorName(actorName(actor));
		job.setActorRole(actor.role());
		job.setStatus(BatchOperationJobStatus.PENDING);
		job.setSelectedCount(selected.size());
		job.setApplicableCount(selected.stream().filter(item -> applicable(command.action(), item.getStatus())).count());
		job.setCreatedAt(now);
		job.setUpdatedAt(now);
		try {
			jobs.save(job);
		} catch (DuplicateKeyException exception) {
			return jobs.findByActorUserIdAndOperationId(actor.userId(), operationId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "BATCH_JOB_CONFLICT", "批处理任务已存在"));
		}
		List<BatchOperationSnapshot> records = new ArrayList<>(selected.size());
		for (int index = 0; index < selected.size(); index++) {
			TaskItem item = selected.get(index);
			BatchOperationSnapshot snapshot = new BatchOperationSnapshot();
			snapshot.setId(job.getId() + ":" + index);
			snapshot.setJobId(job.getId());
			snapshot.setSequence(index);
			snapshot.setItemId(item.getId());
			snapshot.setExpectedRevision(item.getRevision());
			snapshot.setStatus(item.getStatus());
			snapshot.setCollectorId(item.getCollectorId());
			snapshot.setCurrentText(item.getCurrentResult() == null ? null : item.getCurrentResult().text());
			records.add(snapshot);
		}
		snapshots.saveAll(records);
		enqueue(job.getId());
		return jobs.findById(job.getId()).orElse(job);
	}

	public BatchOperationJob get(String id, PlatformPrincipal actor) {
		BatchOperationJob job = requireJob(id);
		if (actor == null || !job.getActorUserId().equals(actor.userId()) && actor.role() != UserRole.ADMIN) {
			throw forbidden();
		}
		resumeExpired(job);
		return job;
	}

	public List<BatchOperationJob> recent(
		String taskId, BatchOperationSource source, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.REVIEWER) throw forbidden();
		List<BatchOperationJob> recent = jobs.findRecent(actor.userId(), taskId, source, 10);
		recent.forEach(this::resumeExpired);
		return recent;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void scheduleStartupRecovery() {
		try {
			executor.execute(() -> jobs.findRecoverable(Instant.now(clock))
				.forEach(job -> enqueue(job.getId())));
		} catch (TaskRejectedException ignored) {
			// A later page refresh can create or inspect jobs while the next restart retries recovery.
		}
	}

	void process(String jobId) {
		String workerId = UUID.randomUUID().toString();
		Instant now = Instant.now(clock);
		BatchOperationJob job = jobs.acquireLease(jobId, workerId, now, now.plus(LEASE_DURATION)).orElse(null);
		if (job == null) return;
		PlatformPrincipal actor = new PlatformPrincipal(
			"batch-" + job.getId(), job.getActorUserId(), job.getActorName(), job.getActorName(),
			job.getActorRole(), job.getActorRole() == UserRole.COLLECTOR ? SessionType.MINIPROGRAM : SessionType.WEB, false
		);
		for (BatchOperationSnapshot snapshot : snapshots.findAllByJobId(jobId)) {
			if (snapshot.getSequence() < job.getNextSequence()) continue;
			String itemOperation = job.getOperationId() + ":item:" + snapshot.getSequence();
			try {
				if (!applicable(job.getAction(), snapshot.getStatus())) {
					job.setSkippedCount(job.getSkippedCount() + 1);
				} else if (alreadyApplied(snapshot.getItemId(), itemOperation)) {
					job.setSucceededCount(job.getSucceededCount() + 1);
				} else {
					execute(job, snapshot, itemOperation, actor);
					job.setSucceededCount(job.getSucceededCount() + 1);
				}
			} catch (ApiException exception) {
				if ("STALE_STATE".equals(exception.getCode()) || "INVALID_DISCARD_STATE".equals(exception.getCode())) {
					job.setSkippedCount(job.getSkippedCount() + 1);
				} else {
					job.setFailedCount(job.getFailedCount() + 1);
					addFailure(job, snapshot.getItemId(), exception.getCode(), exception.getMessage());
				}
			} catch (RuntimeException exception) {
				job.setFailedCount(job.getFailedCount() + 1);
				addFailure(job, snapshot.getItemId(), "BATCH_ITEM_FAILED", "该条数据处理失败");
			}
			job.setProcessedCount(job.getProcessedCount() + 1);
			job.setNextSequence(snapshot.getSequence() + 1);
			Instant heartbeat = Instant.now(clock);
			BatchOperationJob checkpoint = jobs.checkpoint(
				job, workerId, heartbeat, heartbeat.plus(LEASE_DURATION)
			).orElse(null);
			if (checkpoint == null) return;
			job = checkpoint;
		}
		job.setStatus(job.getFailedCount() == 0
			? BatchOperationJobStatus.COMPLETED
			: job.getSucceededCount() > 0 || job.getSkippedCount() > 0
				? BatchOperationJobStatus.PARTIAL_SUCCESS : BatchOperationJobStatus.FAILED);
		job.setCompletedAt(Instant.now(clock));
		job.setUpdatedAt(job.getCompletedAt());
		jobs.finish(job, workerId);
	}

	private void execute(
		BatchOperationJob job, BatchOperationSnapshot snapshot, String operationId, PlatformPrincipal actor
	) {
		switch (job.getAction()) {
			case STATUS -> administration.changeStatus(
				snapshot.getItemId(), job.getTargetStatus(), snapshot.getCollectorId(),
				operationId, snapshot.getExpectedRevision(), actor
			);
			case RELEASE -> actions.release(
				snapshot.getItemId(), operationId, snapshot.getExpectedRevision(), actor
			);
			case DISCARD -> administration.discard(
				snapshot.getItemId(), operationId, snapshot.getExpectedRevision(), null, actor
			);
			case RESTORE -> administration.restore(
				snapshot.getItemId(), operationId, snapshot.getExpectedRevision(), actor
			);
			case COLLECTOR_ASSIGN -> assignments.assign(
				snapshot.getItemId(), job.getCollectorId(), operationId, snapshot.getExpectedRevision(), actor
			);
			case REVIEW_CLAIM -> reviews.claimItem(
				snapshot.getItemId(), operationId, snapshot.getExpectedRevision(), actor
			);
			case REVIEW_ASSIGN -> reviews.assign(
				snapshot.getItemId(), job.getReviewerId(), operationId, snapshot.getExpectedRevision(), actor
			);
			case REVIEW_APPROVE -> reviews.approve(
				snapshot.getItemId(), operationId, snapshot.getExpectedRevision(), snapshot.getCurrentText(), actor
			);
		}
	}

	private List<TaskItem> selectedItems(BatchOperationSelection selection, PlatformPrincipal actor) {
		if (selection == null || trimToNull(selection.taskId()) == null || selection.source() == null) {
			throw invalid("INVALID_BATCH_SELECTION", "任务和来源页面不能为空");
		}
		Set<String> exclusions = selection.excludedItemIds() == null
			? Set.of() : new HashSet<>(selection.excludedItemIds());
		List<TaskItem> selected = new ArrayList<>();
		int page = 0;
		Page<TaskItem> result;
		do {
			if (selection.source() == BatchOperationSource.REVIEW_QUEUE) {
				result = items.findReviewPoolByTaskId(
					selection.taskId(),
					actor.role() == UserRole.ADMIN,
					actor.role() == UserRole.REVIEWER ? actor.userId() : null,
					new com.recording.platform.review.service.ReviewPoolFilter(
						selection.itemCodes(), selection.itemCodeQuery(), selection.statuses(),
						selection.collectorIds(), selection.reviewerIds(),
						selection.includeUnassignedReviewer(), selection.results()
					),
					PageRequest.of(page++, SNAPSHOT_PAGE_SIZE)
				);
			} else {
				result = items.findAllByTaskId(
					selection.taskId(),
					new com.recording.platform.task.service.TaskItemFilter(
						selection.itemCodes(), selection.itemCodeQuery(), selection.groups(),
						selection.collectorIds(), selection.includeUnassigned(), selection.results(),
						selection.sourceItemIdQuery(), null, null
					),
					PageRequest.of(page++, SNAPSHOT_PAGE_SIZE)
				);
			}
			result.getContent().stream()
				.filter(item -> !exclusions.contains(item.getId()))
				.forEach(selected::add);
		} while (result.hasNext());
		return selected;
	}

	private boolean applicable(BatchOperationAction action, TaskItemStatus status) {
		if (action == null || status == null) return false;
		return switch (action) {
			case STATUS -> status != TaskItemStatus.DISCARDED;
			case RELEASE -> status != TaskItemStatus.AVAILABLE && status != TaskItemStatus.DISCARDED;
			case DISCARD -> status != TaskItemStatus.DISCARDED;
			case RESTORE -> status == TaskItemStatus.DISCARDED;
			case COLLECTOR_ASSIGN -> status == TaskItemStatus.AVAILABLE;
			case REVIEW_CLAIM, REVIEW_ASSIGN -> status == TaskItemStatus.SUBMITTED;
			case REVIEW_APPROVE -> status == TaskItemStatus.REVIEW_PENDING;
		};
	}

	private void requireCommand(BatchOperationCommand command, PlatformPrincipal actor) {
		if (command == null || command.action() == null) throw invalid("BATCH_ACTION_REQUIRED", "批处理动作不能为空");
		requireSelectionAccess(command.selection(), actor);
		if (command.action() == BatchOperationAction.STATUS && command.targetStatus() == null) {
			throw invalid("TARGET_STATUS_REQUIRED", "调整状态必须指定目标状态");
		}
		if (command.action() == BatchOperationAction.REVIEW_ASSIGN && trimToNull(command.reviewerId()) == null) {
			throw invalid("REVIEWER_REQUIRED", "批量分配必须指定审核员");
		}
		if (command.action() == BatchOperationAction.COLLECTOR_ASSIGN
			&& trimToNull(command.collectorId()) == null) {
			throw invalid("COLLECTOR_REQUIRED", "批量分配必须指定采集员");
		}
		if (actor.role() == UserRole.REVIEWER && command.action() != BatchOperationAction.REVIEW_CLAIM) {
			throw forbidden();
		}
	}

	private void requireSelectionAccess(BatchOperationSelection selection, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.REVIEWER) throw forbidden();
		if (selection == null || selection.source() == null) return;
		if (actor.role() == UserRole.REVIEWER && selection.source() != BatchOperationSource.REVIEW_QUEUE) {
			throw forbidden();
		}
	}

	private boolean alreadyApplied(String itemId, String operationId) {
		return items.findById(itemId).map(item -> item.getOperations() != null && item.getOperations().stream()
			.anyMatch(operation -> operationId.equals(operation.getOperationId()))).orElse(false);
	}
	private void addFailure(BatchOperationJob job, String itemId, String code, String message) {
		if (job.getFailures().size() >= MAX_FAILURES) return;
		String safe = message == null ? "该条数据处理失败" : message.replaceAll("(?i)https?://\\S+", "[URL]");
		job.getFailures().add(new BatchOperationFailure(itemId, code == null ? "BATCH_ITEM_FAILED" : code,
			safe.length() > 256 ? safe.substring(0, 256) : safe));
	}
	private void enqueue(String jobId) {
		try {
			executor.execute(() -> process(jobId));
		} catch (TaskRejectedException exception) {
			BatchOperationJob job = requireJob(jobId);
			job.setStatus(BatchOperationJobStatus.FAILED);
			job.setFailedCount(job.getSelectedCount());
			job.setFailures(List.of(new BatchOperationFailure(null, "BATCH_QUEUE_UNAVAILABLE", "批处理队列暂时不可用")));
			job.setCompletedAt(Instant.now(clock));
			job.setUpdatedAt(job.getCompletedAt());
			jobs.save(job);
		}
	}
	private void resumeExpired(BatchOperationJob job) {
		if (job.getStatus() == BatchOperationJobStatus.PROCESSING
			&& job.getLeaseExpiresAt() != null
			&& !job.getLeaseExpiresAt().isAfter(Instant.now(clock))) {
			enqueue(job.getId());
		}
	}
	private BatchOperationJob requireJob(String id) {
		return jobs.findById(id).orElseThrow(() ->
			new ApiException(HttpStatus.NOT_FOUND, "BATCH_JOB_NOT_FOUND", "批处理任务不存在"));
	}
	private String required(String value) {
		if (value == null || value.isBlank()) throw new ApiException(
			HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		return value.trim();
	}
	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private String actorName(PlatformPrincipal actor) { return actor.username() == null ? actor.name() : actor.username(); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作"); }
	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}
}

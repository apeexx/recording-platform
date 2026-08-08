package com.recording.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.recording.platform.batch.model.BatchOperationAction;
import com.recording.platform.batch.model.BatchOperationJob;
import com.recording.platform.batch.model.BatchOperationJobStatus;
import com.recording.platform.batch.model.BatchOperationSnapshot;
import com.recording.platform.batch.model.BatchOperationSource;
import com.recording.platform.batch.service.BatchOperationCommand;
import com.recording.platform.batch.service.BatchOperationSelection;
import com.recording.platform.batch.service.BatchOperationService;
import com.recording.platform.batch.store.BatchOperationJobStore;
import com.recording.platform.batch.store.BatchOperationSnapshotStore;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.importing.TaskItemActionService;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.AdminTaskItemGroup;
import com.recording.platform.task.service.TaskItemResultKind;
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.task.service.TaskItemCollectorAssignmentService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class BatchOperationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T16:00:00Z"), ZoneOffset.UTC);

	@Test
	void previewCountsAllSelectedRowsAndActionApplicableStatuses() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(any(), any(com.recording.platform.task.service.TaskItemFilter.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
			item("available", TaskItemStatus.AVAILABLE, 1),
			item("submitted", TaskItemStatus.SUBMITTED, 2),
			item("discarded", TaskItemStatus.DISCARDED, 3)
		)));
		BatchOperationService service = service(items, mock(BatchOperationJobStore.class),
			mock(BatchOperationSnapshotStore.class));

		var preview = service.preview(
			new BatchOperationSelection("task-1", BatchOperationSource.TASK_POOL, Set.of("available")),
			admin()
		);

		assertThat(preview.selectedCount()).isEqualTo(2);
		assertThat(preview.applicableCounts()).containsEntry(BatchOperationAction.DISCARD, 1L)
			.containsEntry(BatchOperationAction.RESTORE, 1L)
			.containsEntry(BatchOperationAction.RELEASE, 1L);
	}

	@Test
	void collectorAssignmentPreviewCountsOnlyAvailableRows() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(any(), any(com.recording.platform.task.service.TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(
				item("available", TaskItemStatus.AVAILABLE, 1),
				item("pending", TaskItemStatus.RECORDING_PENDING, 2)
			)));
		BatchOperationService service = service(items, mock(BatchOperationJobStore.class),
			mock(BatchOperationSnapshotStore.class));

		var preview = service.preview(
			new BatchOperationSelection("task-1", BatchOperationSource.TASK_POOL, Set.of()), admin()
		);

		assertThat(preview.applicableCounts())
			.containsEntry(BatchOperationAction.COLLECTOR_ASSIGN, 1L);
	}

	@Test
	void collectorAssignmentJobPersistsTargetAndUsesItDuringProcessing() throws Exception {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(any(), any(com.recording.platform.task.service.TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(item("available", TaskItemStatus.AVAILABLE, 4))));
		BatchOperationJobStore jobs = mock(BatchOperationJobStore.class);
		when(jobs.findByActorUserIdAndOperationId("admin-1", "assign-all")).thenReturn(Optional.empty());
		when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		BatchOperationSnapshotStore snapshots = mock(BatchOperationSnapshotStore.class);
		TaskItemCollectorAssignmentService assignments = mock(TaskItemCollectorAssignmentService.class);
		BatchOperationService service = new BatchOperationService(
			items, jobs, snapshots, mock(TaskItemAdministrationService.class),
			mock(TaskItemActionService.class), mock(ReviewService.class), assignments, task -> { }, CLOCK
		);

		BatchOperationJob created = service.create(new BatchOperationCommand(
			"assign-all", BatchOperationAction.COLLECTOR_ASSIGN,
			new BatchOperationSelection("task-1", BatchOperationSource.TASK_POOL, Set.of()),
			null, null, "collector-1"
		), admin());

		assertThat(created.getCollectorId()).isEqualTo("collector-1");

		BatchOperationJob processing = job(BatchOperationAction.COLLECTOR_ASSIGN);
		processing.setCollectorId("collector-1");
		when(jobs.acquireLease(any(), any(), any(), any())).thenReturn(Optional.of(processing));
		when(snapshots.findAllByJobId("job-1")).thenReturn(List.of(
			snapshot(0, "item-1", TaskItemStatus.AVAILABLE, 4)
		));
		when(jobs.checkpoint(any(), any(), any(), any())).thenAnswer(invocation ->
			Optional.of(invocation.getArgument(0)));
		var process = BatchOperationService.class.getDeclaredMethod("process", String.class);
		process.setAccessible(true);
		process.invoke(service, "job-1");

		verify(assignments).assign(
			org.mockito.ArgumentMatchers.eq("item-1"),
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.eq("batch-1:item:0"),
			org.mockito.ArgumentMatchers.eq(4L),
			any(PlatformPrincipal.class)
		);
	}

	@Test
	void collectorAssignmentValidatesTargetBeforeCreatingJob() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(any(), any(com.recording.platform.task.service.TaskItemFilter.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(item("available", TaskItemStatus.AVAILABLE, 4))));
		BatchOperationJobStore jobs = mock(BatchOperationJobStore.class);
		when(jobs.findByActorUserIdAndOperationId("admin-1", "assign-invalid")).thenReturn(Optional.empty());
		TaskItemCollectorAssignmentService assignments = mock(TaskItemCollectorAssignmentService.class);
		doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
			"TASK_GRANT_REQUIRED", "没有该任务的有效授权"))
			.when(assignments).validateTarget("task-1", "collector-1", admin());
		BatchOperationService service = new BatchOperationService(
			items, jobs, mock(BatchOperationSnapshotStore.class), mock(TaskItemAdministrationService.class),
			mock(TaskItemActionService.class), mock(ReviewService.class), assignments, task -> { }, CLOCK
		);

		assertThatThrownBy(() -> service.create(new BatchOperationCommand(
			"assign-invalid", BatchOperationAction.COLLECTOR_ASSIGN,
			new BatchOperationSelection("task-1", BatchOperationSource.TASK_POOL, Set.of()),
			null, null, "collector-1"
		), admin())).isInstanceOfSatisfying(ApiException.class,
			error -> assertThat(error.getCode()).isEqualTo("TASK_GRANT_REQUIRED"));

		verify(jobs, never()).save(any());
	}

	@Test
	void previewUsesTheSameFiltersAsTheVisibleTaskPool() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(
			org.mockito.ArgumentMatchers.eq("task-1"),
			org.mockito.ArgumentMatchers.argThat(filter ->
				filter.group() == AdminTaskItemGroup.PENDING
					&& filter.collectorIds().equals(Set.of("collector-1", "collector-2"))
					&& filter.includeUnassigned()
					&& filter.result() == TaskItemResultKind.AUDIO_ONLY),
			any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of(item("pending", TaskItemStatus.RECORDING_PENDING, 3))));
		BatchOperationService service = service(items, mock(BatchOperationJobStore.class),
			mock(BatchOperationSnapshotStore.class));

		var preview = service.preview(new BatchOperationSelection(
			"task-1", BatchOperationSource.TASK_POOL, Set.of(),
			AdminTaskItemGroup.PENDING, Set.of("collector-1", "collector-2"), true,
			TaskItemResultKind.AUDIO_ONLY
		), admin());

		assertThat(preview.selectedCount()).isEqualTo(1);
	}

	@Test
	void previewUsesMultiValueFiltersAndKeepsLegacySingleValuesCompatible() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(
			org.mockito.ArgumentMatchers.eq("task-1"),
			org.mockito.ArgumentMatchers.argThat(filter ->
				filter.itemCodes().equals(Set.of("T000001-0000001", "T000001-0000002"))
					&& filter.groups().equals(Set.of(AdminTaskItemGroup.PENDING, AdminTaskItemGroup.SUBMITTED))
					&& filter.results().equals(Set.of(TaskItemResultKind.TEXT_ONLY, TaskItemResultKind.AUDIO_ONLY))),
			any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of(item("pending", TaskItemStatus.RECORDING_PENDING, 3))));
		BatchOperationService service = service(items, mock(BatchOperationJobStore.class),
			mock(BatchOperationSnapshotStore.class));

		var preview = service.preview(new BatchOperationSelection(
			"task-1", BatchOperationSource.TASK_POOL, Set.of(),
			AdminTaskItemGroup.PENDING, Set.of(), false, TaskItemResultKind.TEXT_ONLY,
			Set.of("T000001-0000001", "T000001-0000002"),
			Set.of(AdminTaskItemGroup.SUBMITTED),
			Set.of(TaskItemResultKind.AUDIO_ONLY)
		), admin());

		assertThat(preview.selectedCount()).isEqualTo(1);
	}

	@Test
	void createSnapshotsOnlyReviewPoolRowsAndPreservesRevision() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findReviewPoolByTaskId(
			any(), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull(),
			any(com.recording.platform.review.service.ReviewPoolFilter.class), any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of(
			item("submitted", TaskItemStatus.SUBMITTED, 7),
			item("review", TaskItemStatus.REVIEW_PENDING, 9)
		)));
		BatchOperationJobStore jobs = mock(BatchOperationJobStore.class);
		when(jobs.findByActorUserIdAndOperationId("admin-1", "batch-1")).thenReturn(Optional.empty());
		when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		BatchOperationSnapshotStore snapshots = mock(BatchOperationSnapshotStore.class);
		BatchOperationService service = service(items, jobs, snapshots);

		var job = service.create(new BatchOperationCommand(
			"batch-1", BatchOperationAction.REVIEW_APPROVE,
			new BatchOperationSelection("task-1", BatchOperationSource.REVIEW_QUEUE, Set.of("submitted")),
			null, null
		), admin());

		var captor = ArgumentCaptor.forClass(List.class);
		verify(snapshots).saveAll(captor.capture());
		@SuppressWarnings("unchecked")
		var stored = (List<com.recording.platform.batch.model.BatchOperationSnapshot>) captor.getValue();
		assertThat(stored).singleElement().satisfies(snapshot -> {
			assertThat(snapshot.getItemId()).isEqualTo("review");
			assertThat(snapshot.getExpectedRevision()).isEqualTo(9);
		});
		assertThat(job.getSelectedCount()).isEqualTo(1);
	}

	@Test
	void processUsesSnapshotRevisionAndCompletesWithSkippedInapplicableRows() throws Exception {
		BatchOperationJobStore jobs = mock(BatchOperationJobStore.class);
		BatchOperationSnapshotStore snapshots = mock(BatchOperationSnapshotStore.class);
		TaskItemAdministrationService administration = mock(TaskItemAdministrationService.class);
		BatchOperationJob job = job(BatchOperationAction.DISCARD);
		BatchOperationSnapshot applicable = snapshot(0, "item-1", TaskItemStatus.SUBMITTED, 7);
		BatchOperationSnapshot skipped = snapshot(1, "item-2", TaskItemStatus.DISCARDED, 9);
		when(jobs.acquireLease(any(), any(), any(), any())).thenReturn(Optional.of(job));
		when(snapshots.findAllByJobId("job-1")).thenReturn(List.of(applicable, skipped));
		when(jobs.checkpoint(any(), any(), any(), any())).thenAnswer(invocation ->
			Optional.of(invocation.getArgument(0)));
		BatchOperationService service = new BatchOperationService(
			mock(TaskItemStore.class), jobs, snapshots, administration,
			mock(TaskItemActionService.class), mock(ReviewService.class),
			mock(TaskItemCollectorAssignmentService.class), task -> { }, CLOCK
		);

		var process = BatchOperationService.class.getDeclaredMethod("process", String.class);
		process.setAccessible(true);
		process.invoke(service, "job-1");

		PlatformPrincipal batchActor = new PlatformPrincipal(
			"batch-job-1", "admin-1", "管理员", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
		verify(administration).discard("item-1", "batch-1:item:0", 7, null, batchActor);
		verify(administration, never()).discard(
			"item-2", "batch-1:item:1", 9, null, batchActor
		);
		var captor = ArgumentCaptor.forClass(BatchOperationJob.class);
		verify(jobs).finish(captor.capture(), any());
		assertThat(captor.getValue().getStatus()).isEqualTo(BatchOperationJobStatus.COMPLETED);
		assertThat(captor.getValue().getProcessedCount()).isEqualTo(2);
		assertThat(captor.getValue().getSucceededCount()).isEqualTo(1);
		assertThat(captor.getValue().getSkippedCount()).isEqualTo(1);
	}

	@Test
	void readingAnExpiredProcessingJobQueuesLeaseRecovery() {
		BatchOperationJobStore jobs = mock(BatchOperationJobStore.class);
		BatchOperationJob job = job(BatchOperationAction.DISCARD);
		job.setStatus(BatchOperationJobStatus.PROCESSING);
		job.setLeaseExpiresAt(Instant.parse("2026-07-27T15:59:59Z"));
		when(jobs.findById("job-1")).thenReturn(Optional.of(job));
		List<Runnable> queued = new ArrayList<>();
		BatchOperationService service = new BatchOperationService(
			mock(TaskItemStore.class), jobs, mock(BatchOperationSnapshotStore.class),
			mock(TaskItemAdministrationService.class), mock(TaskItemActionService.class),
			mock(ReviewService.class), mock(TaskItemCollectorAssignmentService.class), queued::add, CLOCK
		);

		assertThat(service.get("job-1", admin())).isSameAs(job);
		assertThat(queued).hasSize(1);
	}

	private BatchOperationService service(
		TaskItemStore items, BatchOperationJobStore jobs, BatchOperationSnapshotStore snapshots
	) {
		TaskExecutor idleExecutor = task -> { };
		return new BatchOperationService(
			items, jobs, snapshots,
			mock(TaskItemAdministrationService.class),
			mock(TaskItemActionService.class),
			mock(ReviewService.class),
			mock(TaskItemCollectorAssignmentService.class),
			idleExecutor, CLOCK
		);
	}

	private TaskItem item(String id, TaskItemStatus status, long revision) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId("task-1");
		item.setStatus(status);
		item.setRevision(revision);
		return item;
	}

	private BatchOperationJob job(BatchOperationAction action) {
		BatchOperationJob job = new BatchOperationJob();
		job.setId("job-1");
		job.setOperationId("batch-1");
		job.setAction(action);
		job.setActorUserId("admin-1");
		job.setActorName("管理员");
		job.setActorRole(UserRole.ADMIN);
		job.setStatus(BatchOperationJobStatus.PENDING);
		job.setSelectedCount(2);
		return job;
	}

	private BatchOperationSnapshot snapshot(long sequence, String itemId, TaskItemStatus status, long revision) {
		BatchOperationSnapshot snapshot = new BatchOperationSnapshot();
		snapshot.setJobId("job-1");
		snapshot.setSequence(sequence);
		snapshot.setItemId(itemId);
		snapshot.setStatus(status);
		snapshot.setExpectedRevision(revision);
		return snapshot;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

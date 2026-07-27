package com.recording.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.task.store.TaskItemStore;
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
		when(items.findAllByTaskId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
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
	void createSnapshotsOnlyReviewPoolRowsAndPreservesRevision() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.findAllByTaskId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
			item("available", TaskItemStatus.AVAILABLE, 1),
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
			mock(TaskItemActionService.class), mock(ReviewService.class), task -> { }, CLOCK
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
			mock(ReviewService.class), queued::add, CLOCK
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

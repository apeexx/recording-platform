package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.service.SubmitTaskItemCommand;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.SubmitMutation;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TaskPoolServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
	private TaskStore tasks;
	private TaskVersionStore versions;
	private TaskGrantStore grants;
	private InMemoryItemStore items;
	private TaskPoolService service;
	private PlatformPrincipal collector;

	@BeforeEach
	void setUp() {
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		versions = org.mockito.Mockito.mock(TaskVersionStore.class);
		grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		items = new InMemoryItemStore();
		service = new TaskPoolService(tasks, versions, grants, items, CLOCK);
		collector = principal("collector-1", "张三", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
	}

	@Test
	void existingGlobalAssignmentIsReturnedBeforeGrantChecks() {
		TaskItem existing = item("task-other", "item-existing", TaskItemStatus.RECORDING_PENDING);
		existing.setCollectorId("collector-1");
		existing.setAssignmentId("assignment-existing");
		items.save(existing);

		TaskItem result = service.start("task-requested", collector);

		assertThat(result.getId()).isEqualTo("item-existing");
		verifyNoInteractions(tasks, versions, grants);
	}

	@Test
	void concurrentStartsForOneCollectorReturnOneGlobalPendingItem() throws Exception {
		stubRunningTaskAndGrant("task-1");
		items.save(item("task-1", "item-1", TaskItemStatus.AVAILABLE));
		items.save(item("task-1", "item-2", TaskItemStatus.AVAILABLE));
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<TaskItem>> calls = List.of(
				() -> service.start("task-1", collector),
				() -> service.start("task-1", collector)
			);
			List<TaskItem> claimed = executor.invokeAll(calls).stream().map((future) -> {
				try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
			}).toList();
			assertThat(claimed).extracting(TaskItem::getId).containsOnly(claimed.get(0).getId());
			assertThat(items.data.values()).filteredOn((item) -> item.getStatus() == TaskItemStatus.RECORDING_PENDING)
				.hasSize(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void submissionIsIdempotentAndStaleRevisionUsesThe409ContractWithoutCheckingGrant() {
		TaskItem pending = item("task-1", "item-1", TaskItemStatus.RECORDING_PENDING);
		pending.setCollectorId("collector-1");
		pending.setAssignmentId("assignment-1");
		pending.setRevision(1);
		items.save(pending);
		TaskVersion version = version(false, true);
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		SubmittedRecording audio = new SubmittedRecording(
			"media-1", "recordings/TASK-001/I000001/current.wav", RecordingFormat.WAV,
			1000, 16000, 1, 2500
		);
		SubmitTaskItemCommand command = new SubmitTaskItemCommand("submit-1", "assignment-1", 1, null, audio);

		TaskItemActionResult first = service.submit("item-1", command, collector);
		TaskItemActionResult replay = service.submit("item-1", command, collector);

		assertThat(first.status()).isEqualTo(TaskItemStatus.REVIEW_PENDING);
		assertThat(replay).isEqualTo(first);
		assertThat(items.findById("item-1").orElseThrow().getSubmissions()).hasSize(1);
		verifyNoInteractions(grants);
		assertThatThrownBy(() -> service.submit(
			"item-1",
			new SubmitTaskItemCommand("submit-2", "assignment-1", 1, "stale", audio),
			collector
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(409);
			assertThat(exception.getCode()).isEqualTo("STALE_STATE");
		});
	}

	@Test
	void idempotencyHistoryCannotBeReplayedByAnotherCollector() {
		TaskItem pending = item("task-1", "item-1", TaskItemStatus.RECORDING_PENDING);
		pending.setCollectorId("collector-1");
		pending.setAssignmentId("assignment-1");
		pending.setRevision(1);
		items.save(pending);
		when(versions.findById("version-1")).thenReturn(Optional.of(version(true, true)));
		SubmitTaskItemCommand command = new SubmitTaskItemCommand("submit-private", "assignment-1", 1, "第一版", null);
		service.submit("item-1", command, collector);

		PlatformPrincipal otherCollector = principal(
			"collector-2", "李四", UserRole.COLLECTOR, SessionType.MINIPROGRAM
		);
		assertThatThrownBy(() -> service.submit("item-1", command, otherCollector))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(409);
				assertThat(exception.getCode()).isEqualTo("STALE_STATE");
			});
	}

	@Test
	void rejectKeepsTheCollectorAndReleaseClearsCurrentDataButKeepsHistories() {
		TaskItem pending = item("task-1", "item-1", TaskItemStatus.RECORDING_PENDING);
		pending.setCollectorId("collector-1");
		pending.setAssignmentId("assignment-1");
		pending.setRevision(1);
		items.save(pending);
		when(versions.findById("version-1")).thenReturn(Optional.of(version(true, true)));
		TaskItemActionResult submitted = service.submit(
			"item-1",
			new SubmitTaskItemCommand("submit-1", "assignment-1", 1, "第一版", null),
			collector
		);
		PlatformPrincipal reviewer = principal("reviewer-1", "李审", UserRole.REVIEWER, SessionType.WEB);

		TaskItemActionResult rejected = service.reject("item-1", "reject-1", submitted.revision(), "噪音过大", reviewer);
		assertThat(rejected.status()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		assertThat(items.findById("item-1").orElseThrow().getCollectorId()).isEqualTo("collector-1");

		TaskItemActionResult resubmitted = service.submit(
			"item-1",
			new SubmitTaskItemCommand("submit-2", "assignment-1", rejected.revision(), "第二版", null),
			collector
		);
		TaskItemActionResult released = service.release(
			"item-1", "release-1", resubmitted.revision(),
			principal("admin-1", "管理员", UserRole.ADMIN, SessionType.WEB)
		);

		TaskItem saved = items.findById("item-1").orElseThrow();
		assertThat(released.status()).isEqualTo(TaskItemStatus.AVAILABLE);
		assertThat(saved.getCollectorId()).isNull();
		assertThat(saved.getAssignmentId()).isNull();
		assertThat(saved.getCurrentResult()).isNull();
		assertThat(saved.getSubmissions()).hasSize(2);
		assertThat(saved.getOperations()).hasSize(4);
	}

	private void stubRunningTaskAndGrant(String taskId) {
		TaskRecord task = new TaskRecord();
		task.setId(taskId);
		task.setLifecycle(TaskLifecycle.RUNNING);
		when(tasks.findById(taskId)).thenReturn(Optional.of(task));
		TaskGrant grant = new TaskGrant();
		grant.setTaskId(taskId);
		grant.setUserId("collector-1");
		grant.setStatus(GrantStatus.ACTIVE);
		when(grants.findActive(taskId, "collector-1")).thenReturn(Optional.of(grant));
	}

	private TaskVersion version(boolean textInput, boolean humanReview) {
		TaskVersion version = new TaskVersion();
		version.setId("version-1");
		version.setReferenceTypes(Set.of(ReferenceType.TEXT));
		version.setTextInputEnabled(textInput);
		version.setHumanReviewEnabled(humanReview);
		version.setRecordingFormat(RecordingFormat.WAV);
		version.setSampleRates(Set.of(16000));
		version.setChannels(1);
		version.setMinDurationMillis(1000);
		version.setMaxDurationMillis(600000);
		version.setRejectionReasons(List.of("噪音过大"));
		return version;
	}

	private TaskItem item(String taskId, String id, TaskItemStatus status) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId(taskId);
		item.setTaskVersionId("version-1");
		item.setItemCode(id.toUpperCase());
		item.setStatus(status);
		return item;
	}

	private PlatformPrincipal principal(String id, String username, UserRole role, SessionType type) {
		return new PlatformPrincipal("session-" + id, id, username, username, role, type, false);
	}

	private static final class InMemoryItemStore implements TaskItemStore {
		private final Map<String, TaskItem> data = new HashMap<>();
		@Override public synchronized TaskItem save(TaskItem item) { data.put(item.getId(), item); return item; }
		@Override public synchronized Optional<TaskItem> findById(String id) { return Optional.ofNullable(data.get(id)); }
		@Override public synchronized Optional<TaskItem> findCurrentByCollector(String collectorId) {
			return data.values().stream().filter((item) -> collectorId.equals(item.getCollectorId())
				&& item.getStatus() == TaskItemStatus.RECORDING_PENDING).findFirst();
		}
		@Override public synchronized Optional<TaskItem> claimAvailable(ClaimMutation mutation) {
			Optional<TaskItem> current = findCurrentByCollector(mutation.collectorId());
			if (current.isPresent()) return current;
			Optional<TaskItem> candidate = data.values().stream().filter((item) -> mutation.taskId().equals(item.getTaskId())
				&& item.getStatus() == TaskItemStatus.AVAILABLE).findFirst();
			candidate.ifPresent((item) -> {
				item.setStatus(TaskItemStatus.RECORDING_PENDING);
				item.setCollectorId(mutation.collectorId());
				item.setAssignmentId(mutation.assignmentId());
				item.setRevision(item.getRevision() + 1);
				item.getOperations().add(OperationHistory.claim(mutation, item));
			});
			return candidate;
		}
		@Override public synchronized Optional<TaskItem> submitIfCurrent(SubmitMutation mutation) {
			TaskItem item = data.get(mutation.itemId());
			if (!matches(item, mutation.collectorId(), mutation.assignmentId(), mutation.expectedRevision(), TaskItemStatus.RECORDING_PENDING)) return Optional.empty();
			item.setCurrentResult(mutation.result());
			item.setStatus(mutation.targetStatus());
			item.setRevision(item.getRevision() + 1);
			item.getSubmissions().add(SubmissionHistory.from(mutation));
			item.getOperations().add(OperationHistory.submission(mutation, item));
			return Optional.of(item);
		}
		@Override public synchronized Optional<TaskItem> rejectIfCurrent(RejectMutation mutation) {
			TaskItem item = data.get(mutation.itemId());
			if (item == null || item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != mutation.expectedRevision()) return Optional.empty();
			item.setStatus(TaskItemStatus.RECORDING_PENDING);
			item.setRevision(item.getRevision() + 1);
			item.getSubmissions().get(item.getSubmissions().size() - 1).setReviewConclusion(mutation.reason());
			item.getOperations().add(OperationHistory.rejection(mutation, item));
			return Optional.of(item);
		}
		@Override public synchronized Optional<TaskItem> releaseIfCurrent(ReleaseMutation mutation) {
			TaskItem item = data.get(mutation.itemId());
			if (item == null || item.getRevision() != mutation.expectedRevision() || item.getStatus() == TaskItemStatus.AVAILABLE
				|| item.getStatus() == TaskItemStatus.DISCARDED) return Optional.empty();
			if (!mutation.admin() && (item.getStatus() != TaskItemStatus.RECORDING_PENDING
				|| !mutation.actorUserId().equals(item.getCollectorId()))) return Optional.empty();
			item.setStatus(TaskItemStatus.AVAILABLE);
			item.setCollectorId(null);
			item.setReviewerId(null);
			item.setAssignmentId(null);
			item.setCurrentResult(null);
			item.setRevision(item.getRevision() + 1);
			item.getOperations().add(OperationHistory.release(mutation, item));
			return Optional.of(item);
		}
		@Override public Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable) {
			List<TaskItem> found = data.values().stream().filter((item) -> taskId.equals(item.getTaskId())).toList();
			return new PageImpl<>(found, pageable, found.size());
		}
		private boolean matches(TaskItem item, String collectorId, String assignmentId, long revision, TaskItemStatus status) {
			return item != null && item.getStatus() == status && collectorId.equals(item.getCollectorId())
				&& assignmentId.equals(item.getAssignmentId()) && item.getRevision() == revision;
		}
	}
}

package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.model.TaskConfiguration;
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
	private TaskGrantStore grants;
	private InMemoryItemStore items;
	private TaskPoolService service;
	private PlatformPrincipal collector;

	@BeforeEach
	void setUp() {
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		items = new InMemoryItemStore();
		service = new TaskPoolService(tasks, grants, items, CLOCK);
		collector = principal("collector-1", "张三", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
	}

	@Test
	void existingAssignmentForTheRequestedTaskDoesNotBlockANewClaim() {
		TaskItem existing = item("task-requested", "item-existing", TaskItemStatus.RECORDING_PENDING);
		existing.setCollectorId("collector-1");
		existing.setAssignmentId("assignment-existing");
		items.save(existing);
		stubRunningTaskAndGrant("task-requested");
		items.save(item("task-requested", "item-new", TaskItemStatus.AVAILABLE));

		TaskItem result = service.start("task-requested", collector);

		assertThat(result.getId()).isEqualTo("item-new");
		assertThat(items.data.values()).filteredOn((item) -> item.getStatus() == TaskItemStatus.RECORDING_PENDING)
			.hasSize(2);
	}

	@Test
	void existingAssignmentForAnotherTaskDoesNotBlockAClaim() {
		TaskItem existing = item("task-other", "item-existing", TaskItemStatus.RECORDING_PENDING);
		existing.setCollectorId("collector-1");
		existing.setAssignmentId("assignment-existing");
		items.save(existing);
		stubRunningTaskAndGrant("task-requested");
		items.save(item("task-requested", "item-requested", TaskItemStatus.AVAILABLE));

		TaskItem result = service.start("task-requested", collector);

		assertThat(result.getId()).isEqualTo("item-requested");
		assertThat(items.data.values()).filteredOn((item) -> item.getStatus() == TaskItemStatus.RECORDING_PENDING)
			.extracting(TaskItem::getTaskId)
			.containsExactlyInAnyOrder("task-other", "task-requested");
	}

	@Test
	void concurrentStartsForOneCollectorClaimTwoPendingItemsForTheTask() throws Exception {
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
			assertThat(claimed).extracting(TaskItem::getId).containsExactlyInAnyOrder("item-1", "item-2");
			assertThat(items.data.values()).filteredOn((item) -> item.getStatus() == TaskItemStatus.RECORDING_PENDING)
				.hasSize(2);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentStartsForDifferentTasksCanEachClaimOneItem() throws Exception {
		stubRunningTaskAndGrant("task-1");
		stubRunningTaskAndGrant("task-2");
		items.save(item("task-1", "item-1", TaskItemStatus.AVAILABLE));
		items.save(item("task-2", "item-2", TaskItemStatus.AVAILABLE));
		var executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<TaskItem>> calls = List.of(
				() -> service.start("task-1", collector),
				() -> service.start("task-2", collector)
			);
			List<TaskItem> claimed = executor.invokeAll(calls).stream().map((future) -> {
				try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
			}).toList();
			assertThat(claimed).extracting(TaskItem::getTaskId).containsExactlyInAnyOrder("task-1", "task-2");
			assertThat(items.data.values()).filteredOn((item) -> item.getStatus() == TaskItemStatus.RECORDING_PENDING)
				.hasSize(2);
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
		stubConfiguration("task-1", configuration(false, true));
		SubmittedRecording audio = new SubmittedRecording(
			"media-1", "recordings/TASK-001/I000001/current.wav", RecordingFormat.WAV,
			1000, 16000, 1, 2500
		);
		SubmitTaskItemCommand command = new SubmitTaskItemCommand("submit-1", "assignment-1", 1, null, audio);

		TaskItemActionResult first = service.submit("item-1", command, collector);
		TaskItemActionResult replay = service.submit("item-1", command, collector);

		assertThat(first.status()).isEqualTo(TaskItemStatus.SUBMITTED);
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
		stubConfiguration("task-1", configuration(true, true));
		SubmitTaskItemCommand command = new SubmitTaskItemCommand(
			"submit-private", "assignment-1", 1, "第一版", recording()
		);
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
	void textResultAllowsTextOnlyAndRejectsAnEmptyResult() {
		TaskItem pending = item("task-1", "item-text", TaskItemStatus.RECORDING_PENDING);
		pending.setCollectorId("collector-1");
		pending.setAssignmentId("assignment-1");
		pending.setRevision(1);
		items.save(pending);
		stubConfiguration("task-1", configuration(true, true));

		TaskItemActionResult result = service.submit(
			"item-text", new SubmitTaskItemCommand("text-only", "assignment-1", 1, "文本", null), collector
		);
		assertThat(result.status()).isEqualTo(TaskItemStatus.SUBMITTED);

		TaskItem empty = item("task-1", "item-empty", TaskItemStatus.RECORDING_PENDING);
		empty.setCollectorId("collector-1");
		empty.setAssignmentId("assignment-2");
		empty.setRevision(1);
		items.save(empty);
		assertThatThrownBy(() -> service.submit(
			"item-empty", new SubmitTaskItemCommand("empty", "assignment-2", 1, " ", null), collector
		)).isInstanceOfSatisfying(ApiException.class,
			error -> assertThat(error.getCode()).isEqualTo("RESULT_REQUIRED"));
	}

	@Test
	void textResultAllowsAudioOnlyAndTextWithAudio() {
		stubConfiguration("task-1", configuration(true, false));
		TaskItem audioOnly = item("task-1", "audio-only", TaskItemStatus.RECORDING_PENDING);
		audioOnly.setCollectorId("collector-1");
		audioOnly.setAssignmentId("assignment-audio");
		audioOnly.setRevision(1);
		items.save(audioOnly);
		assertThat(service.submit("audio-only", new SubmitTaskItemCommand(
			"audio-only-op", "assignment-audio", 1, null, recording()
		), collector).status()).isEqualTo(TaskItemStatus.COMPLETED);

		TaskItem both = item("task-1", "both", TaskItemStatus.RECORDING_PENDING);
		both.setCollectorId("collector-1");
		both.setAssignmentId("assignment-both");
		both.setRevision(1);
		items.save(both);
		TaskItemActionResult result = service.submit("both", new SubmitTaskItemCommand(
			"both-op", "assignment-both", 1, "文本", recording()
		), collector);
		assertThat(result.result().text()).isEqualTo("文本");
		assertThat(result.result().audio()).isNotNull();
	}

	@Test
	void submittedItemCanBeReplacedBeforeReviewClaim() {
		TaskItem submitted = item("task-1", "item-submitted", TaskItemStatus.SUBMITTED);
		submitted.setCollectorId("collector-1");
		submitted.setAssignmentId("assignment-1");
		submitted.setRevision(2);
		items.save(submitted);
		stubConfiguration("task-1", configuration(true, true));

		TaskItemActionResult result = service.submit(
			"item-submitted",
			new SubmitTaskItemCommand("submit-replace", "assignment-1", 2, "修改版", recording()),
			collector
		);

		assertThat(result.status()).isEqualTo(TaskItemStatus.SUBMITTED);
		assertThat(items.findById("item-submitted").orElseThrow().getSubmissions()).hasSize(1);
	}

	@Test
	void rejectKeepsTheCollectorAndReleaseClearsCurrentDataButKeepsHistories() {
		TaskItem pending = item("task-1", "item-1", TaskItemStatus.RECORDING_PENDING);
		pending.setCollectorId("collector-1");
		pending.setAssignmentId("assignment-1");
		pending.setRevision(1);
		items.save(pending);
		stubConfiguration("task-1", configuration(true, true));
		TaskItemActionResult submitted = service.submit(
			"item-1",
			new SubmitTaskItemCommand("submit-1", "assignment-1", 1, "第一版", recording()),
			collector
		);
		TaskItem waitingReview = items.findById("item-1").orElseThrow();
		waitingReview.setStatus(TaskItemStatus.REVIEW_PENDING);
		waitingReview.setReviewerId("reviewer-1");
		waitingReview.setReviewAssignmentId("review-assignment-1");
		PlatformPrincipal reviewer = principal("reviewer-1", "李审", UserRole.REVIEWER, SessionType.WEB);

		TaskItemActionResult rejected = service.reject("item-1", "reject-1", submitted.revision(), "噪音过大", reviewer);
		assertThat(rejected.status()).isEqualTo(TaskItemStatus.REWORK_PENDING);
		assertThat(items.findById("item-1").orElseThrow().getCollectorId()).isEqualTo("collector-1");

		TaskItemActionResult resubmitted = service.submit(
			"item-1",
			new SubmitTaskItemCommand("submit-2", "assignment-1", rejected.revision(), "第二版", recording()),
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

	private TaskConfiguration configuration(boolean textInput, boolean humanReview) {
		TaskConfiguration configuration = new TaskConfiguration();
		configuration.setReferenceTypes(Set.of(ReferenceType.TEXT));
		configuration.setResultType(textInput ? TaskResultType.TEXT : TaskResultType.AUDIO);
		configuration.setHumanReviewEnabled(humanReview);
		configuration.setRecordingFormat(RecordingFormat.WAV);
		configuration.setSampleRates(Set.of(16000));
		configuration.setChannels(1);
		configuration.setMinDurationMillis(1000);
		configuration.setMaxDurationMillis(600000);
		configuration.setRejectionReasons(List.of("噪音过大"));
		return configuration;
	}

	private void stubConfiguration(String taskId, TaskConfiguration configuration) {
		TaskRecord task = new TaskRecord();
		task.setId(taskId);
		task.setLifecycle(TaskLifecycle.RUNNING);
		task.setConfiguration(configuration);
		when(tasks.findById(taskId)).thenReturn(Optional.of(task));
	}

	private SubmittedRecording recording() {
		return new SubmittedRecording(
			"media-text", "T000001/T000001-0000001.wav", RecordingFormat.WAV,
			3200, 16000, 1, 2500
		);
	}

	private TaskItem item(String taskId, String id, TaskItemStatus status) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId(taskId);
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
		@Override public synchronized Optional<TaskItem> claimAvailable(ClaimMutation mutation) {
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
			if (item == null || (item.getStatus() != TaskItemStatus.RECORDING_PENDING
				&& item.getStatus() != TaskItemStatus.REWORK_PENDING
				&& item.getStatus() != TaskItemStatus.SUBMITTED)
				|| !mutation.collectorId().equals(item.getCollectorId())
				|| !mutation.assignmentId().equals(item.getAssignmentId())
				|| item.getRevision() != mutation.expectedRevision()) return Optional.empty();
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
			item.setStatus(TaskItemStatus.REWORK_PENDING);
			item.setRevision(item.getRevision() + 1);
			item.getSubmissions().get(item.getSubmissions().size() - 1).setReviewConclusion(mutation.reason());
			item.getOperations().add(OperationHistory.rejection(mutation, item));
			return Optional.of(item);
		}
		@Override public synchronized Optional<TaskItem> releaseIfCurrent(ReleaseMutation mutation) {
			TaskItem item = data.get(mutation.itemId());
			if (item == null || item.getRevision() != mutation.expectedRevision() || item.getStatus() == TaskItemStatus.AVAILABLE
				|| item.getStatus() == TaskItemStatus.DISCARDED) return Optional.empty();
			if (!mutation.admin() && ((item.getStatus() != TaskItemStatus.RECORDING_PENDING
				&& item.getStatus() != TaskItemStatus.REWORK_PENDING)
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

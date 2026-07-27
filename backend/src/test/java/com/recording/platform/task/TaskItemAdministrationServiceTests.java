package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.AdminItemTransitionMutation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import java.util.List;

class TaskItemAdministrationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-12T08:00:00Z"), ZoneOffset.UTC);

	@Test
	void ordinaryStatusChangeCannotEnterAvailableOrDisabledReviewStage() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		stubTask(tasks, version(false));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		assertCode(() -> service.changeStatus("item-1", TaskItemStatus.AVAILABLE, null, "op-1", 5, admin()),
			"RELEASE_REQUIRED");
		assertCode(() -> service.changeStatus("item-1", TaskItemStatus.REVIEW_PENDING, null, "op-2", 5, admin()),
			"REVIEW_CLAIM_REQUIRED");
		assertCode(() -> service.changeStatus("item-1", TaskItemStatus.SUBMITTED, null, "op-4", 5, admin()),
			"STATUS_NOT_ENABLED");
		assertCode(() -> service.changeStatus("item-1", TaskItemStatus.AI_PROCESSING, null, "op-3", 5, admin()),
			"STATUS_NOT_ENABLED");
	}

	@Test
	void statusChangeUsesRevisionAndTaskConfigurationRules() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		stubTask(tasks, version(true));
		TaskItem updated = item(TaskItemStatus.SUBMITTED, 6);
		when(items.adminTransitionIfCurrent(any())).thenReturn(Optional.of(updated));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		TaskItem result = service.changeStatus(
			"item-1", TaskItemStatus.SUBMITTED, null, "op-status", 5, admin()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.SUBMITTED);
		assertThat(result.getRevision()).isEqualTo(6);
	}

	@Test
	void discardAndRestorePreserveResultOwnershipAndPreviousStatus() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem completed = item(TaskItemStatus.COMPLETED, 8);
		completed.setCollectorId("collector-1");
		completed.setAssignmentId("assignment-1");
		completed.setCurrentResult(new TaskItemResult(null, "有效文本"));
		when(items.findById("item-1")).thenReturn(Optional.of(completed));
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 9);
		discarded.setDiscardedPreviousStatus(TaskItemStatus.COMPLETED);
		discarded.setCollectorId("collector-1");
		discarded.setAssignmentId("assignment-1");
		discarded.setCurrentResult(completed.getCurrentResult());
		when(items.adminDiscardIfCurrent(any())).thenReturn(Optional.of(discarded));
		stubTask(tasks, version(true));
		TaskItem restored = item(TaskItemStatus.COMPLETED, 10);
		restored.setCollectorId("collector-1");
		restored.setAssignmentId("assignment-1");
		restored.setCurrentResult(completed.getCurrentResult());
		when(items.adminRestoreIfCurrent(any())).thenReturn(Optional.of(restored));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		TaskItem discardResult = service.discard("item-1", "op-discard", 8, null, admin());
		when(items.findById("item-1")).thenReturn(Optional.of(discardResult));
		TaskItem restoreResult = service.restore("item-1", "op-restore", 9, admin());

		assertThat(discardResult.getCurrentResult().text()).isEqualTo("有效文本");
		assertThat(discardResult.getCollectorId()).isEqualTo("collector-1");
		assertThat(restoreResult.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
	}

	@Test
	void collectorCanDiscardOwnPendingItemWithRequiredReason() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem pending = item(TaskItemStatus.RECORDING_PENDING, 3);
		pending.setCollectorId("collector-1");
		when(items.findById("item-1")).thenReturn(Optional.of(pending));
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 4);
		when(items.adminDiscardIfCurrent(any())).thenReturn(Optional.of(discarded));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		service.discard("item-1", "discard-1", 3, "  原始素材无效  ", collector("collector-1"));

		var captor = org.mockito.ArgumentCaptor.forClass(AdminItemTransitionMutation.class);
		verify(items).adminDiscardIfCurrent(captor.capture());
		assertThat(captor.getValue().reason()).isEqualTo("原始素材无效");
		assertThat(captor.getValue().actorRole()).isEqualTo(UserRole.COLLECTOR);
	}

	@Test
	void collectorDiscardRequiresReasonAndOwnedRecordableStatus() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem pending = item(TaskItemStatus.RECORDING_PENDING, 3);
		pending.setCollectorId("collector-1");
		when(items.findById("item-1")).thenReturn(Optional.of(pending));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		assertCode(() -> service.discard(
			"item-1", "discard-empty", 3, " ", collector("collector-1")
		), "INVALID_DISCARD_REASON");
		assertCode(() -> service.discard(
			"item-1", "discard-other", 3, "无效", collector("collector-2")
		), "ACCESS_DENIED");

		pending.setCollectorId("collector-1");
		pending.setStatus(TaskItemStatus.SUBMITTED);
		assertCode(() -> service.discard(
			"item-1", "discard-submitted", 3, "无效", collector("collector-1")
		), "INVALID_DISCARD_STATE");
	}

	@Test
	void collectorCanRestoreOwnDiscardedRecordingItem() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 6);
		discarded.setCollectorId("collector-1");
		discarded.setDiscardedPreviousStatus(TaskItemStatus.REWORK_PENDING);
		when(items.findById("item-1")).thenReturn(Optional.of(discarded));
		stubTask(tasks, version(true));
		TaskItem restored = item(TaskItemStatus.REWORK_PENDING, 7);
		when(items.adminRestoreIfCurrent(any())).thenReturn(Optional.of(restored));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		TaskItem result = service.restore("item-1", "restore-1", 6, collector("collector-1"));

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.REWORK_PENDING);
	}

	@Test
	void batchDiscardReturnsPerItemSuccessAndConflict() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem first = item(TaskItemStatus.COMPLETED, 2);
		TaskItem second = item(TaskItemStatus.COMPLETED, 4);
		second.setId("item-2");
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 3);
		when(items.adminDiscardIfCurrent(any())).thenReturn(Optional.of(discarded)).thenReturn(Optional.empty());
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		List<BatchItemResult> results = service.batchDiscard(
			"batch-discard",
			List.of(new BatchItemCommand("item-1", 2, null), new BatchItemCommand("item-2", 4, null)),
			admin()
		);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(0).revision()).isEqualTo(3);
		assertThat(results.get(1).success()).isFalse();
		assertThat(results.get(1).code()).isEqualTo("STALE_STATE");
	}

	@Test
	void batchStatusUsesTheSameDynamicStateRulesPerItem() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		stubTask(tasks, version(false));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		List<BatchItemResult> results = service.batchChangeStatus(
			"batch-status", TaskItemStatus.SUBMITTED,
			List.of(new BatchItemCommand("item-1", 5, null)), admin()
		);

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.success()).isFalse();
			assertThat(result.code()).isEqualTo("STATUS_NOT_ENABLED");
		});
	}

	@Test
	void humanReviewItemsCannotBypassReviewDecisionToComplete() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem item = item(TaskItemStatus.SUBMITTED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		stubTask(tasks, version(true));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, tasks, CLOCK);

		assertCode(() -> service.changeStatus(
			"item-1", TaskItemStatus.COMPLETED, null, "op-complete", 5, admin()
		), "REVIEW_DECISION_REQUIRED");
	}

	private TaskItem item(TaskItemStatus status, long revision) {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskId("task-1");
		item.setStatus(status);
		item.setRevision(revision);
		return item;
	}

	private TaskConfiguration version(boolean review) {
		TaskConfiguration configuration = new TaskConfiguration();
		configuration.setHumanReviewEnabled(review);
		configuration.setAiEnabled(false);
		return configuration;
	}

	private void stubTask(TaskStore tasks, TaskConfiguration configuration) {
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setConfiguration(configuration);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}

	private PlatformPrincipal collector(String userId) {
		return new PlatformPrincipal(
			"session-" + userId, userId, userId, "采集员", UserRole.COLLECTOR, SessionType.MINIPROGRAM, false
		);
	}

	private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo(code));
	}
}

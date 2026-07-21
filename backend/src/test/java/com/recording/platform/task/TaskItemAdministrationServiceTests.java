package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskVersionStore;
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
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		TaskVersion version = version(false);
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

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
	void statusChangeUsesRevisionAndTaskVersionRules() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		when(versions.findById("version-1")).thenReturn(Optional.of(version(true)));
		TaskItem updated = item(TaskItemStatus.SUBMITTED, 6);
		when(items.adminTransitionIfCurrent(any())).thenReturn(Optional.of(updated));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

		TaskItem result = service.changeStatus(
			"item-1", TaskItemStatus.SUBMITTED, null, "op-status", 5, admin()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.SUBMITTED);
		assertThat(result.getRevision()).isEqualTo(6);
	}

	@Test
	void discardAndRestorePreserveResultOwnershipAndPreviousStatus() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
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
		when(versions.findById("version-1")).thenReturn(Optional.of(version(true)));
		TaskItem restored = item(TaskItemStatus.COMPLETED, 10);
		restored.setCollectorId("collector-1");
		restored.setAssignmentId("assignment-1");
		restored.setCurrentResult(completed.getCurrentResult());
		when(items.adminRestoreIfCurrent(any())).thenReturn(Optional.of(restored));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

		TaskItem discardResult = service.discard("item-1", "op-discard", 8, admin());
		when(items.findById("item-1")).thenReturn(Optional.of(discardResult));
		TaskItem restoreResult = service.restore("item-1", "op-restore", 9, admin());

		assertThat(discardResult.getCurrentResult().text()).isEqualTo("有效文本");
		assertThat(discardResult.getCollectorId()).isEqualTo("collector-1");
		assertThat(restoreResult.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
	}

	@Test
	void batchDiscardReturnsPerItemSuccessAndConflict() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem first = item(TaskItemStatus.COMPLETED, 2);
		TaskItem second = item(TaskItemStatus.COMPLETED, 4);
		second.setId("item-2");
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 3);
		when(items.adminDiscardIfCurrent(any())).thenReturn(Optional.of(discarded)).thenReturn(Optional.empty());
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

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
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem item = item(TaskItemStatus.COMPLETED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		when(versions.findById("version-1")).thenReturn(Optional.of(version(false)));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

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
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem item = item(TaskItemStatus.SUBMITTED, 5);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		when(versions.findById("version-1")).thenReturn(Optional.of(version(true)));
		TaskItemAdministrationService service = new TaskItemAdministrationService(items, versions, CLOCK);

		assertCode(() -> service.changeStatus(
			"item-1", TaskItemStatus.COMPLETED, null, "op-complete", 5, admin()
		), "REVIEW_DECISION_REQUIRED");
	}

	private TaskItem item(TaskItemStatus status, long revision) {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskVersionId("version-1");
		item.setStatus(status);
		item.setRevision(revision);
		return item;
	}

	private TaskVersion version(boolean review) {
		TaskVersion version = new TaskVersion();
		version.setId("version-1");
		version.setHumanReviewEnabled(review);
		version.setAiEnabled(false);
		return version;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}

	private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo(code));
	}
}

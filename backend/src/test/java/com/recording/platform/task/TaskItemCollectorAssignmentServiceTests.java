package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.TaskItemCollectorAssignmentService;
import com.recording.platform.task.store.AdminItemTransitionMutation;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskItemCollectorAssignmentServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T08:00:00Z"), ZoneOffset.UTC);
	private TaskItemStore items;
	private TaskStore tasks;
	private TaskGrantStore grants;
	private IdentityDirectory users;
	private TaskItemCollectorAssignmentService service;

	@BeforeEach
	void setUp() {
		items = mock(TaskItemStore.class);
		tasks = mock(TaskStore.class);
		grants = mock(TaskGrantStore.class);
		users = mock(IdentityDirectory.class);
		service = new TaskItemCollectorAssignmentService(items, tasks, grants, users, CLOCK);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task(TaskLifecycle.RUNNING)));
		when(users.findById("collector-1")).thenReturn(Optional.of(collector(UserStatus.ACTIVE)));
		when(grants.findActive("task-1", "collector-1")).thenReturn(Optional.of(grant()));
	}

	@Test
	void assignsAvailableItemWithNewCollectorAssignment() {
		TaskItem available = item("item-1", TaskItemStatus.AVAILABLE, 4);
		TaskItem assigned = item("item-1", TaskItemStatus.RECORDING_PENDING, 5);
		assigned.setCollectorId("collector-1");
		when(items.findById("item-1")).thenReturn(Optional.of(available));
		when(items.adminTransitionIfCurrent(any())).thenReturn(Optional.of(assigned));

		TaskItem result = service.assign("item-1", "collector-1", "assign-1", 4, admin());

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		ArgumentCaptor<AdminItemTransitionMutation> captor = ArgumentCaptor.forClass(AdminItemTransitionMutation.class);
		verify(items).adminTransitionIfCurrent(captor.capture());
		assertThat(captor.getValue().sourceStatus()).isEqualTo(TaskItemStatus.AVAILABLE);
		assertThat(captor.getValue().targetStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		assertThat(captor.getValue().collectorId()).isEqualTo("collector-1");
		assertThat(captor.getValue().assignmentId()).isNotBlank();
	}

	@Test
	void rejectsAssignmentWhenTaskIsNotRunning() {
		when(items.findById("item-1")).thenReturn(Optional.of(item("item-1", TaskItemStatus.AVAILABLE, 4)));
		when(tasks.findById("task-1")).thenReturn(Optional.of(task(TaskLifecycle.PAUSED)));

		assertCode(() -> service.assign("item-1", "collector-1", "assign-1", 4, admin()), "TASK_NOT_RUNNING");
	}

	@Test
	void rejectsInactiveOrNonCollectorTarget() {
		when(items.findById("item-1")).thenReturn(Optional.of(item("item-1", TaskItemStatus.AVAILABLE, 4)));
		when(users.findById("collector-1")).thenReturn(Optional.of(collector(UserStatus.DISABLED)));

		assertCode(() -> service.assign("item-1", "collector-1", "assign-1", 4, admin()), "INVALID_COLLECTOR");
	}

	@Test
	void rejectsCollectorWithoutActiveTaskGrant() {
		when(items.findById("item-1")).thenReturn(Optional.of(item("item-1", TaskItemStatus.AVAILABLE, 4)));
		when(grants.findActive("task-1", "collector-1")).thenReturn(Optional.empty());

		assertCode(() -> service.assign("item-1", "collector-1", "assign-1", 4, admin()), "TASK_GRANT_REQUIRED");
	}

	@Test
	void refusesToReassignNonAvailableOrChangedItems() {
		when(items.findById("item-1")).thenReturn(Optional.of(item("item-1", TaskItemStatus.RECORDING_PENDING, 5)));

		assertCode(() -> service.assign("item-1", "collector-1", "assign-1", 4, admin()), "STALE_STATE");
	}

	@Test
	void batchAssignmentReturnsPerItemSuccessAndConflict() {
		TaskItem first = item("item-1", TaskItemStatus.AVAILABLE, 2);
		TaskItem second = item("item-2", TaskItemStatus.AVAILABLE, 4);
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		when(items.adminTransitionIfCurrent(any()))
			.thenReturn(Optional.of(item("item-1", TaskItemStatus.RECORDING_PENDING, 3)))
			.thenReturn(Optional.empty());

		var results = service.batchAssign(
			"batch-assign", "collector-1",
			List.of(new BatchItemCommand("item-1", 2, null), new BatchItemCommand("item-2", 4, null)),
			admin()
		);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(1).success()).isFalse();
		assertThat(results.get(1).code()).isEqualTo("STALE_STATE");
	}

	private TaskItem item(String id, TaskItemStatus status, long revision) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId("task-1");
		item.setStatus(status);
		item.setRevision(revision);
		return item;
	}

	private TaskRecord task(TaskLifecycle lifecycle) {
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setLifecycle(lifecycle);
		return task;
	}

	private IdentityUser collector(UserStatus status) {
		return new IdentityUser(
			"collector-1", UserType.MINIPROGRAM, "123456", "采集员", UserRole.COLLECTOR,
			status, false, null, null
		);
	}

	private TaskGrant grant() {
		TaskGrant grant = new TaskGrant();
		grant.setTaskId("task-1");
		grant.setUserId("collector-1");
		grant.setStatus(GrantStatus.ACTIVE);
		return grant;
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

package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.CollectorWorkKind;
import com.recording.platform.task.service.TaskItemQueryService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;

class TaskItemQueryServiceTests {
	@Test
	void collectorCanOnlyReadTheItemCurrentlyAssignedToThem() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setTaskId("task-1");
		item.setCollectorId("collector-1");
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		TaskItemQueryService service = new TaskItemQueryService(items, org.mockito.Mockito.mock(TaskStore.class));

		PlatformPrincipal owner = principal("collector-1");
		assertThat(service.get("item-1", owner)).isSameAs(item);
		assertThatThrownBy(() -> service.get("item-1", principal("collector-2")))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(403);
				assertThat(exception.getCode()).isEqualTo("TASK_ITEM_ACCESS_DENIED");
			});
	}

	@Test
	void submittedAndFinishedKindsUseTheNewWorkflowGroups() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		when(items.findAllByCollectorIdAndStatusIn(
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(List.of(TaskItemStatus.SUBMITTED)),
			org.mockito.ArgumentMatchers.any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of()));
		when(items.findAllByCollectorIdAndStatusIn(
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(List.of(TaskItemStatus.REVIEW_PENDING, TaskItemStatus.COMPLETED)),
			org.mockito.ArgumentMatchers.any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of()));
		TaskItemQueryService service = new TaskItemQueryService(items, tasks);

		service.mine(null, CollectorWorkKind.SUBMITTED, 0, 20, principal("collector-1"));
		service.mine(null, CollectorWorkKind.FINISHED, 0, 20, principal("collector-1"));
	}

	@Test
	void collectorWorkUsesUpdatedTimeDescendingWithSequenceTieBreak() {
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		when(items.findAllByCollectorIdAndStatusIn(
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(List.of(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING)),
			org.mockito.ArgumentMatchers.any(Pageable.class)
		)).thenReturn(new PageImpl<>(List.of()));
		TaskItemQueryService service = new TaskItemQueryService(items, tasks);

		service.mine(null, CollectorWorkKind.PENDING, 2, 20, principal("collector-1"));

		var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		org.mockito.Mockito.verify(items).findAllByCollectorIdAndStatusIn(
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(List.of(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING)),
			captor.capture()
		);
		Pageable pageable = captor.getValue();
		assertThat(pageable.getPageNumber()).isEqualTo(2);
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort().getOrderFor("updatedAt")).isNotNull()
			.extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
		assertThat(pageable.getSort().getOrderFor("sequence")).isNotNull()
			.extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.ASC);
		assertThat(pageable.getSort().getOrderFor("status")).isNull();
	}

	private PlatformPrincipal principal(String userId) {
		return new PlatformPrincipal(
			"session-" + userId,
			userId,
			userId,
			userId,
			UserRole.COLLECTOR,
			SessionType.MINIPROGRAM,
			false
		);
	}
}

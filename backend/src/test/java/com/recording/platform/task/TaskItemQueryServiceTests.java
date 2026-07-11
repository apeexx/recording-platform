package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskItemQueryService;
import com.recording.platform.task.store.TaskItemStore;
import java.util.Optional;
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
		TaskItemQueryService service = new TaskItemQueryService(items);

		PlatformPrincipal owner = principal("collector-1");
		assertThat(service.get("item-1", owner)).isSameAs(item);
		assertThatThrownBy(() -> service.get("item-1", principal("collector-2")))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(403);
				assertThat(exception.getCode()).isEqualTo("TASK_ITEM_ACCESS_DENIED");
			});
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

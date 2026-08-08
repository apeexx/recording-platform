package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.controller.TaskItemCollectorAssignmentController;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import com.recording.platform.task.service.TaskItemCollectorAssignmentService;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskItemCollectorAssignmentControllerTests {
	@Test
	void exposesBatchCollectorAssignmentContract() {
		TaskItemCollectorAssignmentService service = mock(TaskItemCollectorAssignmentService.class);
		TaskItemCollectorAssignmentController controller = new TaskItemCollectorAssignmentController(service);
		PlatformPrincipal admin = admin();
		var requestItems = List.of(
			new TaskItemCollectorAssignmentController.AssignItemRequest("item-1", 2L)
		);
		var serviceItems = List.of(new BatchItemCommand("item-1", 2, null));
		var results = List.of(BatchItemResult.success("item-1", 3));
		when(service.batchAssign("batch-assign", "collector-1", serviceItems, admin)).thenReturn(results);

		assertThat(controller.assign(new TaskItemCollectorAssignmentController.AssignCollectorRequest(
			"batch-assign", "collector-1", requestItems
		), admin)).isSameAs(results);

		verify(service).batchAssign("batch-assign", "collector-1", serviceItems, admin);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.controller.TaskItemAdministrationController;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemAdministrationService;
import org.junit.jupiter.api.Test;
import java.util.List;
import com.recording.platform.task.service.BatchItemResult;
import com.recording.platform.importing.TaskItemActionService;

class TaskItemAdministrationControllerTests {
	@Test
	void exposesStatusDiscardAndRestoreContracts() {
		TaskItemAdministrationService service = mock(TaskItemAdministrationService.class);
		TaskItemAdministrationController controller = new TaskItemAdministrationController(
			service, mock(TaskItemActionService.class)
		);
		PlatformPrincipal admin = admin();
		TaskItem reviewPending = item(TaskItemStatus.SUBMITTED, 6);
		when(service.changeStatus(
			"item-1", TaskItemStatus.SUBMITTED, null, "status-1", 5, admin
		)).thenReturn(reviewPending);
		TaskItem discarded = item(TaskItemStatus.DISCARDED, 7);
		when(service.discard("item-1", "discard-1", 6, admin)).thenReturn(discarded);
		TaskItem restored = item(TaskItemStatus.SUBMITTED, 8);
		when(service.restore("item-1", "restore-1", 7, admin)).thenReturn(restored);

		assertThat(controller.changeStatus("item-1", new TaskItemAdministrationController.StatusRequest(
			"status-1", 5L, TaskItemStatus.SUBMITTED, null
		), admin)).isSameAs(reviewPending);
		assertThat(controller.discard("item-1", new TaskItemAdministrationController.ItemOperationRequest(
			"discard-1", 6L
		), admin)).isSameAs(discarded);
		assertThat(controller.restore("item-1", new TaskItemAdministrationController.ItemOperationRequest(
			"restore-1", 7L
		), admin)).isSameAs(restored);

		verify(service).changeStatus("item-1", TaskItemStatus.SUBMITTED, null, "status-1", 5, admin);
		verify(service).discard("item-1", "discard-1", 6, admin);
		verify(service).restore("item-1", "restore-1", 7, admin);
	}

	@Test
	void exposesBatchStatusDiscardAndRestoreContracts() {
		TaskItemAdministrationService service = mock(TaskItemAdministrationService.class);
		TaskItemActionService actions = mock(TaskItemActionService.class);
		TaskItemAdministrationController controller = new TaskItemAdministrationController(service, actions);
		PlatformPrincipal admin = admin();
		var requestItems = List.of(new TaskItemAdministrationController.BatchItemRequest("item-1", 2L, null));
		var serviceItems = List.of(new com.recording.platform.task.service.BatchItemCommand("item-1", 2, null));
		List<BatchItemResult> results = List.of(BatchItemResult.success("item-1", 3));
		when(service.batchChangeStatus("batch-status", TaskItemStatus.COMPLETED, serviceItems, admin))
			.thenReturn(results);
		when(service.batchDiscard("batch-discard", serviceItems, admin)).thenReturn(results);
		when(service.batchRestore("batch-restore", serviceItems, admin)).thenReturn(results);
		when(actions.batchRelease("batch-release", serviceItems, admin)).thenReturn(results);

		assertThat(controller.batchStatus(new TaskItemAdministrationController.BatchStatusRequest(
			"batch-status", TaskItemStatus.COMPLETED, requestItems
		), admin)).isSameAs(results);
		assertThat(controller.batchDiscard(new TaskItemAdministrationController.BatchOperationRequest(
			"batch-discard", requestItems
		), admin)).isSameAs(results);
		assertThat(controller.batchRestore(new TaskItemAdministrationController.BatchOperationRequest(
			"batch-restore", requestItems
		), admin)).isSameAs(results);
		assertThat(controller.batchRelease(new TaskItemAdministrationController.BatchOperationRequest(
			"batch-release", requestItems
		), admin)).isSameAs(results);
	}

	private TaskItem item(TaskItemStatus status, long revision) {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setStatus(status);
		item.setRevision(revision);
		return item;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

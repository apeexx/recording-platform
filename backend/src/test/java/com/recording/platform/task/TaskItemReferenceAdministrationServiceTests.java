package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.importing.SafeRemoteMediaDownloader;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaCleanupService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskItemReferenceAdministrationService;
import com.recording.platform.task.service.UpdateTaskItemReferencesCommand;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskItemReferenceAdministrationServiceTests {
	private TaskItemStore items;
	private TaskStore tasks;
	private TaskItemReferenceAdministrationService service;
	private PlatformPrincipal admin;
	private TaskItem available;

	@BeforeEach
	void setUp() {
		items = org.mockito.Mockito.mock(TaskItemStore.class);
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		available = new TaskItem();
		available.setId("item-1");
		available.setTaskId("task-1");
		available.setItemCode("T000001-0000001");
		available.setStatus(TaskItemStatus.AVAILABLE);
		available.setRevision(3);
		TaskConfiguration configuration = new TaskConfiguration();
		configuration.setReferenceTypes(Set.of(ReferenceType.TEXT));
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setConfiguration(configuration);
		when(items.findById("item-1")).thenReturn(Optional.of(available));
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(items.updateReferencesIfAvailable(any())).thenAnswer((invocation) -> {
			available.setReferenceText(invocation.<com.recording.platform.task.store.UpdateTaskItemReferencesMutation>getArgument(0).referenceText());
			available.setRevision(4);
			return Optional.of(available);
		});
		service = new TaskItemReferenceAdministrationService(
			items, tasks,
			org.mockito.Mockito.mock(SafeRemoteMediaDownloader.class),
			org.mockito.Mockito.mock(MediaAssetStore.class),
			org.mockito.Mockito.mock(MediaCleanupService.class),
			Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC)
		);
		admin = new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	@Test
	void updatesOnlyAvailableItemWithExpectedRevision() {
		TaskItem updated = service.update(
			"item-1", new UpdateTaskItemReferencesCommand(3, "新文字", null, null), "edit-1", admin
		);
		assertThat(updated.getReferenceText()).isEqualTo("新文字");
		assertThat(updated.getRevision()).isEqualTo(4);
		verify(items).updateReferencesIfAvailable(any());
	}

	@Test
	void staleRevisionCannotEditOrDelete() {
		assertThatThrownBy(() -> service.update(
			"item-1", new UpdateTaskItemReferencesCommand(2, "新文字", null, null), "edit-1", admin
		)).isInstanceOfSatisfying(ApiException.class, (error) ->
			assertThat(error.getCode()).isEqualTo("STALE_STATE")
		);
		assertThatThrownBy(() -> service.delete("item-1", 2, "delete-1", admin))
			.isInstanceOfSatisfying(ApiException.class, (error) ->
				assertThat(error.getCode()).isEqualTo("STALE_STATE")
			);
	}
}

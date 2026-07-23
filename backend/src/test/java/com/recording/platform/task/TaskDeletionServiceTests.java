package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskDeletionService;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskDeletionServiceTests {
	@Test
	void deletesOnlyDraftAndCleansRelatedPermissions() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskGrantStore grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		TaskAccessRequestStore requests = org.mockito.Mockito.mock(TaskAccessRequestStore.class);
		TaskRecord draft = new TaskRecord();
		draft.setId("task-1");
		draft.setLifecycle(TaskLifecycle.DRAFT);
		when(tasks.findById("task-1")).thenReturn(Optional.of(draft));
		when(tasks.deleteDraftById("task-1")).thenReturn(Optional.of(draft));

		new TaskDeletionService(tasks, grants, requests).deleteDraft("task-1");

		verify(grants).deleteAllByTaskId("task-1");
		verify(requests).deleteAllByTaskId("task-1");
	}

	@Test
	void publishedTaskCannotBeDeleted() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskRecord running = new TaskRecord();
		running.setId("task-1");
		running.setLifecycle(TaskLifecycle.RUNNING);
		when(tasks.findById("task-1")).thenReturn(Optional.of(running));
		when(tasks.deleteDraftById("task-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> new TaskDeletionService(
			tasks,
			org.mockito.Mockito.mock(TaskGrantStore.class),
			org.mockito.Mockito.mock(TaskAccessRequestStore.class)
		).deleteDraft("task-1")).isInstanceOfSatisfying(ApiException.class, (error) -> {
			org.assertj.core.api.Assertions.assertThat(error.getStatus().value()).isEqualTo(409);
			org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("INVALID_TASK_STATE");
		});
	}
}

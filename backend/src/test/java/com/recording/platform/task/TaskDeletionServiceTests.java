package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.importing.ImportSourceStorage;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskDeletionService;
import com.recording.platform.task.store.ImportJobStore;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskDeletionServiceTests {
	@Test
	void deletesOnlyDraftAndCleansRelatedPermissions() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskGrantStore grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		TaskAccessRequestStore requests = org.mockito.Mockito.mock(TaskAccessRequestStore.class);
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		ImportJobStore imports = org.mockito.Mockito.mock(ImportJobStore.class);
		ImportSourceStorage sources = org.mockito.Mockito.mock(ImportSourceStorage.class);
		TaskRecord draft = new TaskRecord();
		draft.setId("task-1");
		draft.setLifecycle(TaskLifecycle.DRAFT);
		ImportJob job = new ImportJob();
		job.setSourceRelativePath("temp/imports/job-1/source.csv");
		when(tasks.findById("task-1")).thenReturn(Optional.of(draft));
		when(tasks.deleteDraftById("task-1")).thenReturn(Optional.of(draft));
		when(imports.findAllByTaskId("task-1")).thenReturn(List.of(job));

		new TaskDeletionService(
			tasks, grants, requests, items, imports, sources, fixedClock()
		).deleteDraft("task-1");

		verify(items).deleteAllByTaskId("task-1");
		verify(imports).deleteAllByTaskId("task-1");
		verify(sources).delete("temp/imports/job-1/source.csv");
		verify(grants).deleteAllByTaskId("task-1");
		verify(requests).deleteAllByTaskId("task-1");
	}

	@Test
	void activeImportIsFencedBeforeDeletionAndRequiresSafeRetry() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		ImportJobStore imports = org.mockito.Mockito.mock(ImportJobStore.class);
		TaskRecord draft = new TaskRecord();
		draft.setId("task-1");
		draft.setLifecycle(TaskLifecycle.DRAFT);
		when(tasks.findById("task-1")).thenReturn(Optional.of(draft));
		when(imports.cancelActiveByTaskId("task-1", Instant.parse("2026-07-23T12:00:00Z"))).thenReturn(1L);

		assertThatThrownBy(() -> new TaskDeletionService(
			tasks,
			org.mockito.Mockito.mock(TaskGrantStore.class),
			org.mockito.Mockito.mock(TaskAccessRequestStore.class),
			items,
			imports,
			org.mockito.Mockito.mock(ImportSourceStorage.class),
			fixedClock()
		).deleteDraft("task-1")).isInstanceOfSatisfying(ApiException.class, (error) -> {
			org.assertj.core.api.Assertions.assertThat(error.getStatus().value()).isEqualTo(409);
			org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("IMPORT_JOB_ACTIVE");
		});

		verify(tasks, never()).deleteDraftById("task-1");
		verify(items, never()).deleteAllByTaskId("task-1");
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
			org.mockito.Mockito.mock(TaskAccessRequestStore.class),
			org.mockito.Mockito.mock(TaskItemStore.class),
			org.mockito.Mockito.mock(ImportJobStore.class),
			org.mockito.Mockito.mock(ImportSourceStorage.class),
			fixedClock()
		).deleteDraft("task-1")).isInstanceOfSatisfying(ApiException.class, (error) -> {
			org.assertj.core.api.Assertions.assertThat(error.getStatus().value()).isEqualTo(409);
			org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("INVALID_TASK_STATE");
		});
	}

	private static Clock fixedClock() {
		return Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC);
	}
}

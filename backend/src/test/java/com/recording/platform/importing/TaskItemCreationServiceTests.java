package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.ReferenceMediaUrlValidator;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskItemCreationServiceTests {
	private TaskItemCreationService service;
	private TaskStore tasks;
	private TaskItemStore items;
	private ReferenceMediaUrlValidator referenceUrls;
	private TaskConfiguration configuration;
	private PlatformPrincipal admin;

	@BeforeEach
	void setUp() {
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		items = org.mockito.Mockito.mock(TaskItemStore.class);
		referenceUrls = new ReferenceMediaUrlValidator();
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setTaskCode("T000001");
		task.setLifecycle(TaskLifecycle.RUNNING);
		configuration = new TaskConfiguration();
		configuration.setReferenceTypes(Set.of(ReferenceType.TEXT, ReferenceType.AUDIO, ReferenceType.VIDEO));
		task.setConfiguration(configuration);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(tasks.nextItemSequence("task-1")).thenReturn(1L);
		when(items.findByTaskIdAndCreationOperationId(any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		service = new TaskItemCreationService(
			tasks, items, referenceUrls,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		admin = new PlatformPrincipal("session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	@Test
	void itemRequiresAtLeastOneReference() {
		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand(null, null, null), "add-1", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED");
		});
	}

	@Test
	void importIgnoresColumnsNotEnabledByTaskAndRejectsRowsEmptyAfterFiltering() {
		configuration.setReferenceTypes(Set.of(ReferenceType.AUDIO));
		TaskItem created = service.addImported(
			"task-1",
			new AddTaskItemCommand("应忽略", "https://cdn.example.com/a.wav", "https://cdn.example.com/ignored.mp4"),
			"import-1",
			admin
		);

		assertThat(created.getReferenceText()).isNull();
		assertThat(created.getReferenceVideoUrl()).isNull();
		assertThat(created.getReferenceAudioUrl()).isEqualTo("https://cdn.example.com/a.wav");
		assertThatThrownBy(() -> service.addImported(
			"task-1", new AddTaskItemCommand("仅有禁用文字", null, null), "import-2", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED")
		);
	}

	@Test
	void itemUsesAtomicSequenceAndStoresReferenceUrlsWithoutDownloading() {
		TaskItem created = service.add(
			"task-1",
			new AddTaskItemCommand(
				"请朗读",
				"https://cdn.example.com/audio/object?signature=abc",
				"https://cdn.example.com/video/object#preview"
			),
			"add-1",
			admin
		);

		assertThat(created.getTaskId()).isEqualTo("task-1");
		assertThat(created.getSequence()).isEqualTo(1);
		assertThat(created.getItemCode()).isEqualTo("T000001-0000001");
		assertThat(created.getReferenceAudioMediaId()).isNull();
		assertThat(created.getReferenceVideoMediaId()).isNull();
		assertThat(created.getReferenceAudioUrl()).isEqualTo("https://cdn.example.com/audio/object?signature=abc");
		assertThat(created.getReferenceVideoUrl()).isEqualTo("https://cdn.example.com/video/object#preview");
		assertThat(created.getStatus()).isEqualTo(TaskItemStatus.AVAILABLE);
		assertThat(created.getOperations()).singleElement().satisfies((operation) -> {
			assertThat(operation.getOperationId()).isEqualTo("add-1");
			assertThat(operation.getActorUsername()).isEqualTo("admin");
			assertThat(operation.getContent()).contains("新增了任务条目");
		});
	}

	@Test
	void itemSequenceStopsAtOneMillion() {
		when(tasks.nextItemSequence("task-1")).thenReturn(1_000_001L);

		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand("请朗读", null, null), "add-overflow", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(409);
			assertThat(exception.getCode()).isEqualTo("ITEM_CODE_EXHAUSTED");
		});
	}

}

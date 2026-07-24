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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskItemCreationServiceTests {
	private TaskItemCreationService service;
	private TaskStore tasks;
	private TaskItemStore items;
	private ReferenceMediaUrlValidator referenceUrls;
	private TaskConfiguration configuration;
	private TaskRecord task;
	private PlatformPrincipal admin;

	@BeforeEach
	void setUp() {
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		items = org.mockito.Mockito.mock(TaskItemStore.class);
		referenceUrls = new ReferenceMediaUrlValidator();
		task = new TaskRecord();
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
	void draftAndPausedTasksCanPrepareItems() {
		task.setLifecycle(TaskLifecycle.DRAFT);
		TaskItem draftItem = service.add(
			"task-1", new AddTaskItemCommand("草稿数据", null, null), "draft-add", admin
		);
		task.setLifecycle(TaskLifecycle.PAUSED);
		TaskItem pausedItem = service.add(
			"task-1", new AddTaskItemCommand("暂停数据", null, null), "paused-add", admin
		);

		assertThat(draftItem.getReferenceText()).isEqualTo("草稿数据");
		assertThat(pausedItem.getReferenceText()).isEqualTo("暂停数据");
	}

	@Test
	void endedTaskRejectsNewItems() {
		task.setLifecycle(TaskLifecycle.ENDED);

		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand("不可添加", null, null), "ended-add", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(409);
			assertThat(exception.getCode()).isEqualTo("INVALID_TASK_STATE");
		});
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
	void integrationEntryReusesCreationRulesAndRecordsTheDedicatedActor() {
		TaskItem created = service.addIntegration(
			"task-1",
			new AddTaskItemCommand(
				"外部参考文字",
				"https://cdn.example.com/reference.wav",
				"https://cdn.example.com/reference.mp4"
			),
			"external-add-1"
		);

		assertThat(created.getReferenceText()).isEqualTo("外部参考文字");
		assertThat(created.getReferenceAudioUrl()).isEqualTo("https://cdn.example.com/reference.wav");
		assertThat(created.getReferenceVideoUrl()).isEqualTo("https://cdn.example.com/reference.mp4");
		assertThat(created.getOperations()).singleElement().satisfies((operation) -> {
			assertThat(operation.getActorUserId()).isEqualTo("INTEGRATION-ANNOTATION-SCRIPT-CENTER");
			assertThat(operation.getActorUsername()).isEqualTo("annotation-script-center");
		});
	}

	@ParameterizedTest
	@MethodSource("integrationReferenceCombinations")
	void integrationAcceptsEveryNonEmptyReferenceCombination(
		String text,
		String audioUrl,
		String videoUrl
	) {
		TaskItem created = service.addIntegration(
			"task-1",
			new AddTaskItemCommand(text, audioUrl, videoUrl),
			"combination-" + Integer.toHexString(java.util.Objects.hash(text, audioUrl, videoUrl))
		);

		assertThat(created.getReferenceText()).isEqualTo(text);
		assertThat(created.getReferenceAudioUrl()).isEqualTo(audioUrl);
		assertThat(created.getReferenceVideoUrl()).isEqualTo(videoUrl);
	}

	@Test
	void integrationUsesTheSameEmptyTypeStateAndUrlValidationRules() {
		assertThatThrownBy(() -> service.addIntegration(
			"task-1", new AddTaskItemCommand(" ", null, null), "external-empty"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED")
		);

		configuration.setReferenceTypes(Set.of(ReferenceType.TEXT));
		assertThatThrownBy(() -> service.addIntegration(
			"task-1",
			new AddTaskItemCommand(null, "https://cdn.example.com/reference.wav", null),
			"external-disabled-type"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("REFERENCE_TYPE_NOT_ENABLED")
		);

		configuration.setReferenceTypes(Set.of(ReferenceType.AUDIO));
		assertThatThrownBy(() -> service.addIntegration(
			"task-1",
			new AddTaskItemCommand(null, "http://private.example.com/reference.wav", null),
			"external-invalid-url"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("REMOTE_URL_INVALID")
		);

		task.setLifecycle(TaskLifecycle.ENDED);
		assertThatThrownBy(() -> service.addIntegration(
			"task-1",
			new AddTaskItemCommand(null, "https://cdn.example.com/reference.wav", null),
			"external-ended"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("INVALID_TASK_STATE")
		);
	}

	private static java.util.stream.Stream<Arguments> integrationReferenceCombinations() {
		String audio = "https://cdn.example.com/reference.wav";
		String video = "https://cdn.example.com/reference.mp4";
		return java.util.stream.Stream.of(
			Arguments.of("参考文字", null, null),
			Arguments.of(null, audio, null),
			Arguments.of(null, null, video),
			Arguments.of("参考文字", audio, null),
			Arguments.of("参考文字", null, video),
			Arguments.of(null, audio, video),
			Arguments.of("参考文字", audio, video)
		);
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

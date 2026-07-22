package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.service.CreateTaskCommand;
import com.recording.platform.task.service.TaskConfigurationSpec;
import com.recording.platform.task.service.TaskManagementService;
import com.recording.platform.task.store.SequenceStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TaskManagementServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
	private InMemoryTaskStore tasks;
	private TaskManagementService service;

	@BeforeEach
	void setUp() {
		tasks = new InMemoryTaskStore();
		service = new TaskManagementService(new InMemorySequenceStore(), tasks, CLOCK);
	}

	@Test
	void taskConfigurationRequiresReferencesAndRejectsAi() {
		assertCode(() -> service.create(command(spec(Set.of(), TaskResultType.AUDIO, 1_000, 600_000, false))), "REFERENCE_REQUIRED");
		assertCode(() -> service.create(command(spec(Set.of(ReferenceType.TEXT), TaskResultType.AUDIO, 1_000, 600_000, true))), "AI_NOT_SUPPORTED");
	}

	@Test
	void durationRangeIsRestrictedToOneThroughSixHundredSeconds() {
		assertCode(() -> service.create(command(spec(Set.of(ReferenceType.TEXT), TaskResultType.TEXT, 999, 600_000, false))), "INVALID_DURATION_RANGE");
		assertCode(() -> service.create(command(spec(Set.of(ReferenceType.TEXT), TaskResultType.TEXT, 1_000, 600_001, false))), "INVALID_DURATION_RANGE");
	}

	@Test
	void taskCodesAndConfigurationAreStoredOnTheTask() {
		TaskRecord first = service.create(command(spec(Set.of(ReferenceType.TEXT), TaskResultType.TEXT, 1_000, 600_000, false)));
		TaskRecord second = service.create(command(spec(Set.of(ReferenceType.AUDIO), TaskResultType.AUDIO, 2_000, 30_000, false)));

		assertThat(first.getTaskCode()).isEqualTo("T000001");
		assertThat(second.getTaskCode()).isEqualTo("T000002");
		assertThat(first.getConfiguration().getResultType()).isEqualTo(TaskResultType.TEXT);
		assertThat(first.getConfiguration().getReferenceTypes()).containsExactly(ReferenceType.TEXT);
		assertThat(first.getConfiguration().getRejectionReasons()).containsExactly("噪音过大");
	}

	@Test
	void draftCanBeEditedButPublishedTaskIsFrozen() {
		TaskRecord task = service.create(command(spec(Set.of(ReferenceType.TEXT), TaskResultType.TEXT, 1_000, 600_000, false)));
		TaskRecord edited = service.updateStructure(
			task.getId(), "修订草稿", "新说明",
			spec(Set.of(ReferenceType.AUDIO), TaskResultType.AUDIO, 2_000, 60_000, false)
		);
		assertThat(edited.getName()).isEqualTo("修订草稿");
		assertThat(edited.getConfiguration().getResultType()).isEqualTo(TaskResultType.AUDIO);

		service.publish(task.getId());
		assertThatThrownBy(() -> service.updateStructure(
			task.getId(), "发布后修改", null,
			spec(Set.of(ReferenceType.TEXT), TaskResultType.TEXT, 1_000, 600_000, false)
		)).isInstanceOfSatisfying(ApiException.class, error -> {
			assertThat(error.getStatus().value()).isEqualTo(409);
			assertThat(error.getCode()).isEqualTo("INVALID_TASK_STATE");
		});
	}

	@Test
	void disabledHumanReviewDropsRejectionReasonsAndLifecycleStillWorks() {
		TaskConfigurationSpec configuration = new TaskConfigurationSpec(
			Set.of(ReferenceType.TEXT), TaskResultType.TEXT, false,
			RecordingFormat.WAV, Set.of(16000), 1, 1_000L, 600_000L,
			List.of("不应保存"), false, null, null
		);
		TaskRecord task = service.create(command(configuration));
		assertThat(task.getConfiguration().getRejectionReasons()).isEmpty();
		assertThat(service.publish(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.RUNNING);
		assertThat(service.pause(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.PAUSED);
		assertThat(service.resume(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.RUNNING);
		assertThat(service.end(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.ENDED);
	}

	@Test
	void nullReferenceTypeIsRejected() {
		Set<ReferenceType> references = new HashSet<>();
		references.add(null);
		assertCode(() -> service.create(command(spec(references, TaskResultType.TEXT, 1_000, 600_000, false))), "INVALID_REFERENCE_TYPE");
	}

	private void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
			error -> assertThat(error.getCode()).isEqualTo(code));
	}

	private CreateTaskCommand command(TaskConfigurationSpec configuration) {
		return new CreateTaskCommand("朗读任务", "说明", configuration);
	}

	private TaskConfigurationSpec spec(
		Set<ReferenceType> references, TaskResultType resultType,
		long minDurationMillis, long maxDurationMillis, boolean aiEnabled
	) {
		return new TaskConfigurationSpec(
			references, resultType, true, RecordingFormat.WAV, Set.of(16000), 1,
			minDurationMillis, maxDurationMillis, List.of("噪音过大"), aiEnabled, null, null
		);
	}

	private static final class InMemorySequenceStore implements SequenceStore {
		private long value;
		@Override public long next(String key) { return ++value; }
	}

	private static final class InMemoryTaskStore implements TaskStore {
		private final Map<String, TaskRecord> data = new HashMap<>();
		@Override public TaskRecord save(TaskRecord task) {
			if (task.getId() == null) task.setId(UUID.randomUUID().toString());
			data.put(task.getId(), task);
			return task;
		}
		@Override public void deleteById(String id) { data.remove(id); }
		@Override public Optional<TaskRecord> findById(String id) { return Optional.ofNullable(data.get(id)); }
		@Override public Optional<TaskRecord> findByTaskCode(String taskCode) {
			return data.values().stream().filter(task -> taskCode.equals(task.getTaskCode())).findFirst();
		}
		@Override public Page<TaskRecord> findAll(Pageable pageable) {
			return new PageImpl<>(new ArrayList<>(data.values()), pageable, data.size());
		}
		@Override public long nextItemSequence(String taskId) { return 1; }
	}
}

package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.service.CreateTaskCommand;
import com.recording.platform.task.service.TaskManagementService;
import com.recording.platform.task.service.TaskVersionSpec;
import com.recording.platform.task.store.PlatformStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
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
	private InMemoryPlatformStore platforms;
	private InMemoryTaskStore tasks;
	private InMemoryTaskVersionStore versions;
	private TaskManagementService service;

	@BeforeEach
	void setUp() {
		platforms = new InMemoryPlatformStore();
		tasks = new InMemoryTaskStore();
		versions = new InMemoryTaskVersionStore();
		PlatformRecord platform = new PlatformRecord();
		platform.setCode("WECHAT");
		platform.setName("微信采集");
		platform.setActive(true);
		platforms.save(platform);
		service = new TaskManagementService(platforms, tasks, versions, CLOCK);
	}

	@Test
	void aVersionRequiresAReferenceAndRejectsAiDuringTheFirstPhase() {
		assertThatThrownBy(() -> service.create(command(spec(Set.of(), false))))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(422);
				assertThat(exception.getCode()).isEqualTo("REFERENCE_REQUIRED");
			});

		assertThatThrownBy(() -> service.create(command(spec(Set.of(ReferenceType.TEXT), true))))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(422);
				assertThat(exception.getCode()).isEqualTo("AI_NOT_SUPPORTED");
			});
	}

	@Test
	void taskCodeAndReferenceTypesMatchTheStorageContract() {
		CreateTaskCommand unsafeCode = new CreateTaskCommand(
			"中文/任务", platforms.findByCode("WECHAT").orElseThrow().getId(), "朗读任务", null,
			spec(Set.of(ReferenceType.TEXT), false)
		);
		assertThatThrownBy(() -> service.create(unsafeCode))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("INVALID_TASK_CODE")
			);

		Set<ReferenceType> invalidReferences = new HashSet<>();
		invalidReferences.add(null);
		assertThatThrownBy(() -> service.create(command(spec(invalidReferences, false))))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("INVALID_REFERENCE_TYPE")
			);
	}

	@Test
	void publishingLocksTheSnapshotAndStructuralChangesCreateTheNextVersion() {
		TaskRecord created = service.create(command(spec(Set.of(ReferenceType.TEXT), false)));
		assertThat(created.getLifecycle()).isEqualTo(TaskLifecycle.DRAFT);
		TaskRecord published = service.publish(created.getId());
		TaskVersion versionOne = versions.findById(published.getCurrentVersionId()).orElseThrow();
		assertThat(versionOne.isPublished()).isTrue();
		assertThat(versionOne.getVersionNumber()).isEqualTo(1);
		assertThat(versionOne.getReferenceTypes()).containsExactly(ReferenceType.TEXT);

		TaskRecord changed = service.updateStructure(
			created.getId(),
			"带音频参考的任务",
			"第二版",
			spec(Set.of(ReferenceType.AUDIO), false)
		);
		TaskVersion versionTwo = versions.findById(changed.getCurrentVersionId()).orElseThrow();

		assertThat(changed.getLifecycle()).isEqualTo(TaskLifecycle.RUNNING);
		assertThat(changed.getCurrentVersionNumber()).isEqualTo(2);
		assertThat(versionTwo.isPublished()).isTrue();
		assertThat(versionTwo.getReferenceTypes()).containsExactly(ReferenceType.AUDIO);
		assertThat(versions.findById(versionOne.getId()).orElseThrow().getReferenceTypes())
			.containsExactly(ReferenceType.TEXT);
	}

	@Test
	void taskLifecycleOnlyAllowsTheDocumentedTransitions() {
		TaskRecord task = service.create(command(spec(Set.of(ReferenceType.TEXT), false)));
		service.publish(task.getId());
		assertThat(service.pause(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.PAUSED);
		assertThat(service.resume(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.RUNNING);
		assertThat(service.end(task.getId()).getLifecycle()).isEqualTo(TaskLifecycle.ENDED);
		assertThatThrownBy(() -> service.resume(task.getId()))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("INVALID_TASK_STATE")
			);
	}

	@Test
	void failedTaskPointerUpdateDeletesTheNewPublishedVersion() {
		PlatformStore platformStore = org.mockito.Mockito.mock(PlatformStore.class);
		TaskStore taskStore = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versionStore = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setName("第一版");
		task.setLifecycle(TaskLifecycle.RUNNING);
		task.setCurrentVersionId("version-1");
		task.setCurrentVersionNumber(1);
		TaskVersion current = new TaskVersion();
		current.setId("version-1");
		current.setTaskId("task-1");
		current.setVersionNumber(1);
		current.setPublished(true);
		current.setCreatedAt(Instant.parse("2026-07-11T11:00:00Z"));
		when(taskStore.findById("task-1")).thenReturn(Optional.of(task));
		when(versionStore.findById("version-1")).thenReturn(Optional.of(current));
		when(versionStore.save(any(TaskVersion.class))).thenAnswer((invocation) -> {
			TaskVersion saved = invocation.getArgument(0);
			saved.setId("version-2");
			return saved;
		});
		when(taskStore.save(task)).thenThrow(new IllegalStateException("simulated pointer write failure"));
		TaskManagementService failingService = new TaskManagementService(platformStore, taskStore, versionStore, CLOCK);

		assertThatThrownBy(() -> failingService.updateStructure(
			"task-1", "第二版", null, spec(Set.of(ReferenceType.AUDIO), false)
		)).isInstanceOf(IllegalStateException.class);

		verify(versionStore).deleteById("version-2");
	}

	private CreateTaskCommand command(TaskVersionSpec spec) {
		return new CreateTaskCommand("TASK-001", platforms.findByCode("WECHAT").orElseThrow().getId(), "朗读任务", "说明", spec);
	}

	private TaskVersionSpec spec(Set<ReferenceType> references, boolean aiEnabled) {
		return new TaskVersionSpec(
			references,
			true,
			false,
			true,
			RecordingFormat.WAV,
			Set.of(16000),
			1,
			1000,
			600000,
			List.of("噪音过大"),
			aiEnabled,
			null,
			null
		);
	}

	private static final class InMemoryPlatformStore implements PlatformStore {
		private final Map<String, PlatformRecord> data = new HashMap<>();

		@Override
		public PlatformRecord save(PlatformRecord platform) {
			if (platform.getId() == null) platform.setId(UUID.randomUUID().toString());
			data.put(platform.getId(), platform);
			return platform;
		}

		@Override
		public Optional<PlatformRecord> findById(String id) { return Optional.ofNullable(data.get(id)); }

		@Override
		public Optional<PlatformRecord> findByCode(String code) {
			return data.values().stream().filter((platform) -> code.equals(platform.getCode())).findFirst();
		}

		@Override
		public Page<PlatformRecord> findAll(Pageable pageable) {
			return new PageImpl<>(new ArrayList<>(data.values()), pageable, data.size());
		}

		@Override
		public void deleteById(String id) { data.remove(id); }
	}

	private static final class InMemoryTaskStore implements TaskStore {
		private final Map<String, TaskRecord> data = new HashMap<>();

		@Override
		public TaskRecord save(TaskRecord task) {
			if (task.getId() == null) task.setId(UUID.randomUUID().toString());
			data.put(task.getId(), task);
			return task;
		}

		@Override public void deleteById(String id) { data.remove(id); }

		@Override
		public Optional<TaskRecord> findById(String id) { return Optional.ofNullable(data.get(id)); }

		@Override
		public Optional<TaskRecord> findByTaskCode(String taskCode) {
			return data.values().stream().filter((task) -> taskCode.equals(task.getTaskCode())).findFirst();
		}

		@Override
		public Page<TaskRecord> findAll(Pageable pageable) {
			return new PageImpl<>(new ArrayList<>(data.values()), pageable, data.size());
		}

		@Override
		public long nextItemSequence(String taskId) { return 1; }
	}

	private static final class InMemoryTaskVersionStore implements TaskVersionStore {
		private final Map<String, TaskVersion> data = new HashMap<>();

		@Override
		public TaskVersion save(TaskVersion version) {
			if (version.getId() == null) version.setId(UUID.randomUUID().toString());
			data.put(version.getId(), copy(version));
			return copy(version);
		}

		@Override public void deleteById(String id) { data.remove(id); }

		@Override
		public Optional<TaskVersion> findById(String id) {
			return Optional.ofNullable(data.get(id)).map(this::copy);
		}

		@Override
		public Optional<TaskVersion> findByTaskIdAndVersionNumber(String taskId, int versionNumber) {
			return data.values().stream()
				.filter((version) -> taskId.equals(version.getTaskId()) && version.getVersionNumber() == versionNumber)
				.findFirst().map(this::copy);
		}

		@Override
		public List<TaskVersion> findAllByTaskIdOrderByVersionNumber(String taskId) {
			return data.values().stream().filter((version) -> taskId.equals(version.getTaskId()))
				.sorted(java.util.Comparator.comparingInt(TaskVersion::getVersionNumber)).map(this::copy).toList();
		}

		private TaskVersion copy(TaskVersion source) {
			TaskVersion copy = new TaskVersion();
			copy.setId(source.getId());
			copy.setTaskId(source.getTaskId());
			copy.setVersionNumber(source.getVersionNumber());
			copy.setReferenceTypes(Set.copyOf(source.getReferenceTypes()));
			copy.setFixedRecording(source.isFixedRecording());
			copy.setTextInputEnabled(source.isTextInputEnabled());
			copy.setHumanReviewEnabled(source.isHumanReviewEnabled());
			copy.setRecordingFormat(source.getRecordingFormat());
			copy.setSampleRates(Set.copyOf(source.getSampleRates()));
			copy.setChannels(source.getChannels());
			copy.setMinDurationMillis(source.getMinDurationMillis());
			copy.setMaxDurationMillis(source.getMaxDurationMillis());
			copy.setRejectionReasons(List.copyOf(source.getRejectionReasons()));
			copy.setAiEnabled(source.isAiEnabled());
			copy.setAiProvider(source.getAiProvider());
			copy.setAiModel(source.getAiModel());
			copy.setPublished(source.isPublished());
			copy.setCreatedAt(source.getCreatedAt());
			copy.setPublishedAt(source.getPublishedAt());
			return copy;
		}
	}
}

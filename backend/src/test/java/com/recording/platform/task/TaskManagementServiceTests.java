package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.service.CreateTaskCommand;
import com.recording.platform.task.service.TaskManagementService;
import com.recording.platform.task.service.TaskVersionSpec;
import com.recording.platform.task.store.SequenceStore;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TaskManagementServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
	private InMemorySequenceStore sequences;
	private InMemoryTaskStore tasks;
	private InMemoryTaskVersionStore versions;
	private TaskManagementService service;

	@BeforeEach
	void setUp() {
		sequences = new InMemorySequenceStore();
		tasks = new InMemoryTaskStore();
		versions = new InMemoryTaskVersionStore();
		service = new TaskManagementService(sequences, tasks, versions, CLOCK);
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
	void listsImmutableVersionsForTheTaskEditor() {
		TaskRecord task = service.create(command(spec(Set.of(ReferenceType.TEXT), false)));

		assertThat(service.versions(task.getId()))
			.extracting(TaskVersion::getVersionNumber)
			.containsExactly(1);
		assertThatThrownBy(() -> service.versions("missing"))
			.isInstanceOfSatisfying(ApiException.class, error ->
				assertThat(error.getCode()).isEqualTo("TASK_NOT_FOUND"));
	}

	@Test
	void taskCodesAreGeneratedAndReferenceTypesMatchTheStorageContract() {
		assertThat(service.create(command(spec(Set.of(ReferenceType.TEXT), false))).getTaskCode()).isEqualTo("T000001");
		assertThat(service.create(command(spec(Set.of(ReferenceType.TEXT), false))).getTaskCode()).isEqualTo("T000002");
		Set<ReferenceType> invalidReferences = new HashSet<>();
		invalidReferences.add(null);
		assertThatThrownBy(() -> service.create(command(spec(invalidReferences, false))))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("INVALID_REFERENCE_TYPE")
			);
	}

	@Test
	void textResultKeepsRecordingConfigurationAndDisabledReviewDropsReasons() {
		TaskVersionSpec spec = new TaskVersionSpec(
			Set.of(ReferenceType.TEXT), TaskResultType.TEXT, false,
			RecordingFormat.WAV, Set.of(16000), 1, 1000L, 600000L,
			List.of("不应保存"), false, null, null
		);

		TaskRecord task = service.create(command(spec));
		TaskVersion version = versions.findById(task.getCurrentVersionId()).orElseThrow();

		assertThat(version.getResultType()).isEqualTo(TaskResultType.TEXT);
		assertThat(version.getRecordingFormat()).isEqualTo(RecordingFormat.WAV);
		assertThat(version.getSampleRates()).containsExactly(16000);
		assertThat(version.getRejectionReasons()).isEmpty();
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
		SequenceStore sequenceStore = org.mockito.Mockito.mock(SequenceStore.class);
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
		TaskManagementService failingService = new TaskManagementService(sequenceStore, taskStore, versionStore, CLOCK);

		assertThatThrownBy(() -> failingService.updateStructure(
			"task-1", "第二版", null, spec(Set.of(ReferenceType.AUDIO), false)
		)).isInstanceOf(IllegalStateException.class);

		verify(versionStore).deleteById("version-2");
	}

	@Test
	void publishRestoresTheSavedVersionSnapshotWhenTheTaskStateWriteFails() {
		SequenceStore sequenceStore = org.mockito.Mockito.mock(SequenceStore.class);
		TaskStore taskStore = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versionStore = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setLifecycle(TaskLifecycle.DRAFT);
		task.setCurrentVersionId("version-1");
		TaskVersion current = version("version-1", false, 4L);
		when(taskStore.findById("task-1")).thenReturn(Optional.of(task));
		when(versionStore.findById("version-1")).thenReturn(Optional.of(current));
		List<TaskVersion> savedSnapshots = new ArrayList<>();
		when(versionStore.save(any(TaskVersion.class))).thenAnswer((invocation) -> {
			TaskVersion candidate = invocation.getArgument(0);
			savedSnapshots.add(copyVersion(candidate));
			TaskVersion saved = copyVersion(candidate);
			saved.setVersion(candidate.getVersion() + 1);
			return saved;
		});
		IllegalStateException pointerFailure = new IllegalStateException("simulated task publish failure");
		when(taskStore.save(task)).thenThrow(pointerFailure);
		TaskManagementService failingService = new TaskManagementService(sequenceStore, taskStore, versionStore, CLOCK);

		assertThatThrownBy(() -> failingService.publish("task-1")).isSameAs(pointerFailure);

		assertThat(savedSnapshots).hasSize(2);
		assertThat(savedSnapshots.get(0).isPublished()).isTrue();
		assertThat(savedSnapshots.get(0).getVersion()).isEqualTo(4L);
		assertThat(savedSnapshots.get(1).isPublished()).isFalse();
		assertThat(savedSnapshots.get(1).getPublishedAt()).isNull();
		assertThat(savedSnapshots.get(1).getVersion()).isEqualTo(5L);
	}

	@Test
	void draftRestoreUsesTheVersionReturnedByTheFailedPointerUpdatesPriorSave() {
		SequenceStore sequenceStore = org.mockito.Mockito.mock(SequenceStore.class);
		TaskStore taskStore = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versionStore = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setName("第一版");
		task.setLifecycle(TaskLifecycle.DRAFT);
		task.setCurrentVersionId("version-1");
		task.setCurrentVersionNumber(1);
		TaskVersion current = version("version-1", false, 7L);
		when(taskStore.findById("task-1")).thenReturn(Optional.of(task));
		when(versionStore.findById("version-1")).thenReturn(Optional.of(current));
		List<Long> attemptedVersions = new ArrayList<>();
		AtomicInteger saves = new AtomicInteger();
		when(versionStore.save(any(TaskVersion.class))).thenAnswer((invocation) -> {
			TaskVersion candidate = invocation.getArgument(0);
			attemptedVersions.add(candidate.getVersion());
			if (saves.getAndIncrement() == 1 && candidate.getVersion() != 8L) {
				throw new org.springframework.dao.OptimisticLockingFailureException("stale version");
			}
			TaskVersion saved = copyVersion(candidate);
			saved.setVersion(candidate.getVersion() + 1);
			return saved;
		});
		IllegalStateException pointerFailure = new IllegalStateException("simulated draft pointer failure");
		when(taskStore.save(task)).thenThrow(pointerFailure);
		TaskManagementService failingService = new TaskManagementService(sequenceStore, taskStore, versionStore, CLOCK);

		assertThatThrownBy(() -> failingService.updateStructure(
			"task-1", "修订草稿", null, spec(Set.of(ReferenceType.AUDIO), false)
		)).isSameAs(pointerFailure).satisfies((exception) -> assertThat(exception.getSuppressed()).isEmpty());

		assertThat(attemptedVersions).containsExactly(7L, 8L);
	}

	@Test
	void compensationFailureReturnsAControlledConsistencyError() {
		SequenceStore sequenceStore = org.mockito.Mockito.mock(SequenceStore.class);
		TaskStore taskStore = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versionStore = org.mockito.Mockito.mock(TaskVersionStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setLifecycle(TaskLifecycle.DRAFT);
		task.setCurrentVersionId("version-1");
		TaskVersion current = version("version-1", false, 4L);
		when(taskStore.findById("task-1")).thenReturn(Optional.of(task));
		when(versionStore.findById("version-1")).thenReturn(Optional.of(current));
		when(versionStore.save(any(TaskVersion.class)))
			.thenAnswer((invocation) -> {
				TaskVersion saved = copyVersion(invocation.getArgument(0));
				saved.setVersion(5L);
				return saved;
			})
			.thenThrow(new IllegalStateException("simulated compensation failure"));
		when(taskStore.save(task)).thenThrow(new IllegalStateException("simulated task publish failure"));
		TaskManagementService failingService = new TaskManagementService(sequenceStore, taskStore, versionStore, CLOCK);

		assertThatThrownBy(() -> failingService.publish("task-1"))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(500);
				assertThat(exception.getCode()).isEqualTo("TASK_CONSISTENCY_RECOVERY_FAILED");
				assertThat(exception.getSuppressed()).hasSize(2);
			});
	}

	private CreateTaskCommand command(TaskVersionSpec spec) {
		return new CreateTaskCommand("朗读任务", "说明", spec);
	}

	private TaskVersionSpec spec(Set<ReferenceType> references, boolean aiEnabled) {
		return new TaskVersionSpec(
			references,
			TaskResultType.AUDIO,
			true,
			RecordingFormat.WAV,
			Set.of(16000),
			1,
			1000L,
			600000L,
			List.of("噪音过大"),
			aiEnabled,
			null,
			null
		);
	}

	private static TaskVersion version(String id, boolean published, long optimisticVersion) {
		TaskVersion version = new TaskVersion();
		version.setId(id);
		version.setVersion(optimisticVersion);
		version.setTaskId("task-1");
		version.setVersionNumber(1);
		version.setReferenceTypes(Set.of(ReferenceType.TEXT));
		version.setResultType(TaskResultType.AUDIO);
		version.setRecordingFormat(RecordingFormat.WAV);
		version.setSampleRates(Set.of(16000));
		version.setChannels(1);
		version.setMinDurationMillis(1000);
		version.setMaxDurationMillis(600000);
		version.setPublished(published);
		version.setCreatedAt(Instant.parse("2026-07-11T11:00:00Z"));
		return version;
	}

	private static TaskVersion copyVersion(TaskVersion source) {
		TaskVersion copy = new TaskVersion();
		copy.setId(source.getId());
		copy.setVersion(source.getVersion());
		copy.setTaskId(source.getTaskId());
		copy.setVersionNumber(source.getVersionNumber());
		copy.setReferenceTypes(Set.copyOf(source.getReferenceTypes()));
		copy.setResultType(source.getResultType());
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

	private static final class InMemorySequenceStore implements SequenceStore {
		private long value;
		@Override public long next(String key) { return ++value; }
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
			copy.setResultType(source.getResultType());
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

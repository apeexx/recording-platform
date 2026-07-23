package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.SequenceStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskManagementService {
	private static final Set<Integer> SUPPORTED_SAMPLE_RATES = Set.of(8000, 16000, 24000, 32000, 44100, 48000);
	private static final long MIN_DURATION_MILLIS = 1_000;
	private static final long MAX_DURATION_MILLIS = 600_000;
	private final SequenceStore sequences;
	private final TaskStore tasks;
	private final Clock clock;

	public TaskManagementService(SequenceStore sequences, TaskStore tasks, Clock clock) {
		this.sequences = sequences;
		this.tasks = tasks;
		this.clock = clock;
	}

	public TaskRecord create(CreateTaskCommand command) {
		TaskConfiguration configuration = configuration(command.configuration());
		long taskSequence = sequences.next("task");
		if (taskSequence < 1 || taskSequence > 999999) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CODE_EXHAUSTED", "任务编号已达到上限");
		}
		Instant now = Instant.now(clock);
		TaskRecord task = new TaskRecord();
		task.setTaskCode("T%06d".formatted(taskSequence));
		task.setName(required(command.name(), "INVALID_TASK_NAME", "任务名称不能为空"));
		task.setDescription(trimToNull(command.description()));
		task.setLifecycle(TaskLifecycle.DRAFT);
		task.setConfiguration(configuration);
		task.setCreatedAt(now);
		task.setUpdatedAt(now);
		return tasks.save(task);
	}

	public TaskRecord updateStructure(String taskId, String name, String description, TaskConfigurationSpec spec) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != TaskLifecycle.DRAFT) {
			throw invalidState("任务发布后不可修改");
		}
		task.setName(required(name, "INVALID_TASK_NAME", "任务名称不能为空"));
		task.setDescription(trimToNull(description));
		task.setConfiguration(configuration(spec));
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	public TaskRecord publish(String taskId) {
		return transition(taskId, TaskLifecycle.DRAFT, TaskLifecycle.RUNNING);
	}

	public TaskRecord pause(String taskId) {
		return transition(taskId, TaskLifecycle.RUNNING, TaskLifecycle.PAUSED);
	}

	public TaskRecord resume(String taskId) {
		return transition(taskId, TaskLifecycle.PAUSED, TaskLifecycle.RUNNING);
	}

	public TaskRecord end(String taskId) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != TaskLifecycle.PAUSED) {
			throw invalidState("只有暂停任务可以结束");
		}
		task.setLifecycle(TaskLifecycle.ENDED);
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	public TaskRecord get(String taskId) {
		return requireTask(taskId);
	}

	private TaskRecord transition(String taskId, TaskLifecycle expected, TaskLifecycle target) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != expected) throw invalidState("任务状态不允许该操作");
		task.setLifecycle(target);
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	private TaskConfiguration configuration(TaskConfigurationSpec spec) {
		validateConfiguration(spec);
		TaskConfiguration configuration = new TaskConfiguration();
		configuration.setReferenceTypes(new LinkedHashSet<>(spec.referenceTypes()));
		configuration.setResultType(spec.resultType());
		configuration.setHumanReviewEnabled(spec.humanReviewEnabled());
		configuration.setRecordingFormat(spec.recordingFormat());
		configuration.setSampleRates(new LinkedHashSet<>(spec.sampleRates()));
		configuration.setChannels(spec.channels());
		configuration.setMinDurationMillis(spec.minDurationMillis());
		configuration.setMaxDurationMillis(spec.maxDurationMillis());
		configuration.setRejectionReasons(!spec.humanReviewEnabled() || spec.rejectionReasons() == null
			? List.of() : List.copyOf(spec.rejectionReasons()));
		configuration.setAiEnabled(false);
		configuration.setAiProvider(trimToNull(spec.aiProvider()));
		configuration.setAiModel(trimToNull(spec.aiModel()));
		return configuration;
	}

	private void validateConfiguration(TaskConfigurationSpec spec) {
		if (spec == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "任务配置不能为空");
		if (spec.referenceTypes() == null || spec.referenceTypes().isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_REQUIRED", "至少启用一种参考组件");
		}
		if (spec.referenceTypes().stream().anyMatch(java.util.Objects::isNull)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REFERENCE_TYPE", "参考组件类型不合法");
		}
		if (spec.aiEnabled()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_NOT_SUPPORTED", "首期不支持启用 AI 审核");
		}
		if (spec.resultType() == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RESULT_TYPE_REQUIRED", "请选择最终提交文本或音频");
		}
		if (spec.recordingFormat() == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RECORDING_FORMAT", "录音格式只能是 WAV 或 MP3");
		}
		if (!Integer.valueOf(1).equals(spec.channels())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CHANNELS", "录音声道固定为 1");
		}
		if (spec.sampleRates() == null || spec.sampleRates().isEmpty()
			|| !SUPPORTED_SAMPLE_RATES.containsAll(spec.sampleRates())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SAMPLE_RATE", "采样率不受微信录音端支持");
		}
		if (spec.minDurationMillis() == null || spec.maxDurationMillis() == null
			|| spec.minDurationMillis() < MIN_DURATION_MILLIS
			|| spec.maxDurationMillis() > MAX_DURATION_MILLIS
			|| spec.maxDurationMillis() < spec.minDurationMillis()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION_RANGE", "录音时长范围必须为 1 到 600 秒");
		}
	}

	private TaskRecord requireTask(String taskId) {
		return tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
	}

	private ApiException invalidState(String message) {
		return new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", message);
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
		return value.trim();
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

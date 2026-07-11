package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.store.PlatformStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskManagementService {
	private static final Set<Integer> SUPPORTED_SAMPLE_RATES = Set.of(8000, 16000, 24000, 32000, 44100, 48000);
	private final PlatformStore platforms;
	private final TaskStore tasks;
	private final TaskVersionStore versions;
	private final Clock clock;

	public TaskManagementService(PlatformStore platforms, TaskStore tasks, TaskVersionStore versions, Clock clock) {
		this.platforms = platforms;
		this.tasks = tasks;
		this.versions = versions;
		this.clock = clock;
	}

	public TaskRecord create(CreateTaskCommand command) {
		validateVersion(command.version());
		PlatformRecord platform = platforms.findById(command.platformId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLATFORM_NOT_FOUND", "平台不存在"));
		if (!platform.isActive()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLATFORM_INACTIVE", "平台已停用");
		}
		String taskCode = required(command.taskCode(), "INVALID_TASK_CODE", "任务编码不能为空").toUpperCase(Locale.ROOT);
		if (!taskCode.matches("[A-Z0-9_-]{1,128}")) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_TASK_CODE",
				"任务编码只能包含字母、数字、下划线或连字符，长度为 1 到 128"
			);
		}
		if (tasks.findByTaskCode(taskCode).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CODE_EXISTS", "任务编码已存在");
		}
		Instant now = Instant.now(clock);
		TaskRecord task = new TaskRecord();
		task.setTaskCode(taskCode);
		task.setPlatformId(platform.getId());
		task.setName(required(command.name(), "INVALID_TASK_NAME", "任务名称不能为空"));
		task.setDescription(trimToNull(command.description()));
		task.setLifecycle(TaskLifecycle.DRAFT);
		task.setCreatedAt(now);
		task.setUpdatedAt(now);
		TaskVersion savedVersion = null;
		try {
			task = tasks.save(task);
			TaskVersion version = newVersion(task.getId(), 1, command.version(), false, now);
			savedVersion = versions.save(version);
			task.setCurrentVersionId(savedVersion.getId());
			task.setCurrentVersionNumber(1);
			return tasks.save(task);
		} catch (RuntimeException exception) {
			if (savedVersion != null && savedVersion.getId() != null) {
				String savedVersionId = savedVersion.getId();
				compensate(() -> versions.deleteById(savedVersionId), exception);
			}
			if (task.getId() != null) {
				String savedTaskId = task.getId();
				compensate(() -> tasks.deleteById(savedTaskId), exception);
			}
			throw exception;
		}
	}

	public TaskRecord publish(String taskId) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != TaskLifecycle.DRAFT) {
			throw invalidState("只有草稿任务可以发布");
		}
		TaskVersion version = requireVersion(task.getCurrentVersionId());
		version.setPublished(true);
		version.setPublishedAt(Instant.now(clock));
		versions.save(version);
		task.setLifecycle(TaskLifecycle.RUNNING);
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	public TaskRecord updateStructure(
		String taskId,
		String name,
		String description,
		TaskVersionSpec spec
	) {
		validateVersion(spec);
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() == TaskLifecycle.ENDED) {
			throw invalidState("已结束任务不能修改");
		}
		TaskVersion current = requireVersion(task.getCurrentVersionId());
		Instant now = Instant.now(clock);
		TaskVersion target;
		if (current.isPublished()) {
			target = newVersion(task.getId(), current.getVersionNumber() + 1, spec, true, now);
			target.setPublishedAt(now);
		} else {
			target = newVersion(task.getId(), current.getVersionNumber(), spec, false, current.getCreatedAt());
			target.setId(current.getId());
			target.setVersion(current.getVersion());
		}
		TaskVersion savedTarget = null;
		try {
			savedTarget = versions.save(target);
			task.setName(required(name, "INVALID_TASK_NAME", "任务名称不能为空"));
			task.setDescription(trimToNull(description));
			task.setCurrentVersionId(savedTarget.getId());
			task.setCurrentVersionNumber(savedTarget.getVersionNumber());
			task.setUpdatedAt(now);
			return tasks.save(task);
		} catch (RuntimeException exception) {
			if (savedTarget != null) {
				if (current.isPublished()) {
					String orphanId = savedTarget.getId();
					if (orphanId != null) compensate(() -> versions.deleteById(orphanId), exception);
				} else {
					compensate(() -> versions.save(current), exception);
				}
			}
			throw exception;
		}
	}

	public TaskRecord pause(String taskId) {
		return transition(taskId, TaskLifecycle.RUNNING, TaskLifecycle.PAUSED);
	}

	public TaskRecord resume(String taskId) {
		return transition(taskId, TaskLifecycle.PAUSED, TaskLifecycle.RUNNING);
	}

	public TaskRecord end(String taskId) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != TaskLifecycle.RUNNING && task.getLifecycle() != TaskLifecycle.PAUSED) {
			throw invalidState("只有进行中或暂停任务可以结束");
		}
		task.setLifecycle(TaskLifecycle.ENDED);
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	public TaskRecord get(String taskId) {
		return requireTask(taskId);
	}

	public TaskVersion getVersion(String versionId) {
		return requireVersion(versionId);
	}

	private TaskRecord transition(String taskId, TaskLifecycle expected, TaskLifecycle target) {
		TaskRecord task = requireTask(taskId);
		if (task.getLifecycle() != expected) {
			throw invalidState("任务状态不允许该操作");
		}
		task.setLifecycle(target);
		task.setUpdatedAt(Instant.now(clock));
		return tasks.save(task);
	}

	private TaskVersion newVersion(
		String taskId,
		int number,
		TaskVersionSpec spec,
		boolean published,
		Instant createdAt
	) {
		TaskVersion version = new TaskVersion();
		version.setTaskId(taskId);
		version.setVersionNumber(number);
		version.setReferenceTypes(new LinkedHashSet<>(spec.referenceTypes()));
		version.setFixedRecording(spec.fixedRecording());
		version.setTextInputEnabled(spec.textInputEnabled());
		version.setHumanReviewEnabled(spec.humanReviewEnabled());
		version.setRecordingFormat(spec.recordingFormat());
		version.setSampleRates(new LinkedHashSet<>(spec.sampleRates()));
		version.setChannels(spec.channels());
		version.setMinDurationMillis(spec.minDurationMillis());
		version.setMaxDurationMillis(spec.maxDurationMillis());
		version.setRejectionReasons(spec.rejectionReasons() == null ? List.of() : List.copyOf(spec.rejectionReasons()));
		version.setAiEnabled(false);
		version.setAiProvider(trimToNull(spec.aiProvider()));
		version.setAiModel(trimToNull(spec.aiModel()));
		version.setPublished(published);
		version.setCreatedAt(createdAt);
		return version;
	}

	private void validateVersion(TaskVersionSpec spec) {
		if (spec == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "任务版本配置不能为空");
		}
		if (spec.referenceTypes() == null || spec.referenceTypes().isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFERENCE_REQUIRED", "至少启用一种参考组件");
		}
		if (spec.referenceTypes().stream().anyMatch(java.util.Objects::isNull)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REFERENCE_TYPE", "参考组件类型不合法");
		}
		if (spec.aiEnabled()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_NOT_SUPPORTED", "首期不支持启用 AI 审核");
		}
		if (spec.recordingFormat() == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RECORDING_FORMAT", "录音格式只能是 WAV 或 MP3");
		}
		if (spec.channels() != 1) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CHANNELS", "录音声道固定为 1");
		}
		if (spec.sampleRates() == null || spec.sampleRates().isEmpty()
			|| !SUPPORTED_SAMPLE_RATES.containsAll(spec.sampleRates())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SAMPLE_RATE", "采样率不受微信录音端支持");
		}
		if (spec.minDurationMillis() < 1 || spec.maxDurationMillis() < spec.minDurationMillis()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION_RANGE", "录音时长范围不合法");
		}
	}

	private TaskRecord requireTask(String taskId) {
		return tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
	}

	private TaskVersion requireVersion(String versionId) {
		return versions.findById(versionId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_VERSION_NOT_FOUND", "任务版本不存在"));
	}

	private ApiException invalidState(String message) {
		return new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", message);
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
		}
		return value.trim();
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void compensate(Runnable cleanup, RuntimeException original) {
		try {
			cleanup.run();
		} catch (RuntimeException cleanupFailure) {
			original.addSuppressed(cleanupFailure);
		}
	}
}

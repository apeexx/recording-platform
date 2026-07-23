package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.importing.ImportSourceStorage;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.store.ImportJobStore;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskDeletionService {
	private final TaskStore tasks;
	private final TaskGrantStore grants;
	private final TaskAccessRequestStore accessRequests;
	private final TaskItemStore items;
	private final ImportJobStore imports;
	private final ImportSourceStorage importSources;
	private final Clock clock;

	public TaskDeletionService(
		TaskStore tasks,
		TaskGrantStore grants,
		TaskAccessRequestStore accessRequests,
		TaskItemStore items,
		ImportJobStore imports,
		ImportSourceStorage importSources,
		Clock clock
	) {
		this.tasks = tasks;
		this.grants = grants;
		this.accessRequests = accessRequests;
		this.items = items;
		this.imports = imports;
		this.importSources = importSources;
		this.clock = clock;
	}

	public TaskRecord deleteDraft(String taskId) {
		TaskRecord existing = tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (existing.getLifecycle() != TaskLifecycle.DRAFT) {
			throw new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", "仅草稿任务可以删除");
		}
		long cancelled = imports.cancelActiveByTaskId(taskId, Instant.now(clock));
		if (cancelled > 0) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"IMPORT_JOB_ACTIVE",
				"导入任务已安全停止，请稍后重试删除草稿"
			);
		}
		var importJobs = imports.findAllByTaskId(taskId);
		TaskRecord deleted = tasks.deleteDraftById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", "仅草稿任务可以删除"));
		items.deleteAllByTaskId(taskId);
		for (var job : importJobs) importSources.delete(job.getSourceRelativePath());
		imports.deleteAllByTaskId(taskId);
		grants.deleteAllByTaskId(taskId);
		accessRequests.deleteAllByTaskId(taskId);
		return deleted.getId() == null ? existing : deleted;
	}
}

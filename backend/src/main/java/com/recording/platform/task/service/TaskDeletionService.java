package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskDeletionService {
	private final TaskStore tasks;
	private final TaskGrantStore grants;
	private final TaskAccessRequestStore accessRequests;

	public TaskDeletionService(TaskStore tasks, TaskGrantStore grants, TaskAccessRequestStore accessRequests) {
		this.tasks = tasks;
		this.grants = grants;
		this.accessRequests = accessRequests;
	}

	public TaskRecord deleteDraft(String taskId) {
		TaskRecord existing = tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		TaskRecord deleted = tasks.deleteDraftById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INVALID_TASK_STATE", "仅草稿任务可以删除"));
		grants.deleteAllByTaskId(taskId);
		accessRequests.deleteAllByTaskId(taskId);
		return deleted.getId() == null ? existing : deleted;
	}
}

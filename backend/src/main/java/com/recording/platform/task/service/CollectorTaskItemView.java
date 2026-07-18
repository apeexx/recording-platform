package com.recording.platform.task.service;

import com.recording.platform.task.model.CurrentRejection;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;

public record CollectorTaskItemView(
	String id,
	String taskId,
	String taskCode,
	String taskName,
	String itemCode,
	TaskItemStatus status,
	long revision,
	String assignmentId,
	CurrentRejection currentRejection
) {
	static CollectorTaskItemView from(TaskItem item, com.recording.platform.task.model.TaskRecord task) {
		return new CollectorTaskItemView(
			item.getId(), item.getTaskId(), task == null ? null : task.getTaskCode(),
			task == null ? null : task.getName(), item.getItemCode(), item.getStatus(), item.getRevision(),
			item.getAssignmentId(), item.getCurrentRejection()
		);
	}
}

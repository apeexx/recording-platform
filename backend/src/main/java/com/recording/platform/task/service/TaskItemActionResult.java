package com.recording.platform.task.service;

import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;

public record TaskItemActionResult(
	String itemId,
	TaskItemStatus status,
	long revision,
	String assignmentId,
	TaskItemResult result
) {
	public static TaskItemActionResult from(TaskItem item) {
		return new TaskItemActionResult(
			item.getId(), item.getStatus(), item.getRevision(), item.getAssignmentId(), item.getCurrentResult()
		);
	}

	public static TaskItemActionResult replay(String itemId, OperationHistory operation) {
		return new TaskItemActionResult(
			itemId,
			operation.getResultStatus(),
			operation.getResultRevision(),
			operation.getResultAssignmentId(),
			operation.getResultSnapshot()
		);
	}
}

package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItemStatus;
import java.util.List;

public enum AdminTaskItemGroup {
	ALL,
	PENDING,
	SUBMITTED,
	FINISHED,
	DISCARDED;

	public List<TaskItemStatus> statuses() {
		return switch (this) {
			case ALL -> List.of();
			case PENDING -> List.of(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING);
			case SUBMITTED -> List.of(TaskItemStatus.SUBMITTED);
			case FINISHED -> List.of(TaskItemStatus.REVIEW_PENDING, TaskItemStatus.COMPLETED);
			case DISCARDED -> List.of(TaskItemStatus.DISCARDED);
		};
	}
}

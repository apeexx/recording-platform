package com.recording.platform.integration;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record IntegrationResultView(
	String itemId,
	String taskId,
	String itemCode,
	TaskItemStatus status,
	Instant updatedAt,
	String text,
	boolean audioAvailable
) {
	static IntegrationResultView from(TaskItem item) {
		TaskItemResult result = item.getStatus() == TaskItemStatus.COMPLETED
			? item.getCurrentResult()
			: null;
		return new IntegrationResultView(
			item.getId(),
			item.getTaskId(),
			item.getItemCode(),
			item.getStatus(),
			item.getUpdatedAt(),
			result == null ? null : result.text(),
			result != null && result.audio() != null
		);
	}
}

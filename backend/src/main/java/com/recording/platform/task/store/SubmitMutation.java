package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record SubmitMutation(
	String itemId,
	String collectorId,
	String actorUsername,
	String assignmentId,
	long expectedRevision,
	String operationId,
	TaskItemResult result,
	TaskItemStatus targetStatus,
	Instant occurredAt
) {
}

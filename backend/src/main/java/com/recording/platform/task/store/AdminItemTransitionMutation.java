package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record AdminItemTransitionMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	long expectedRevision,
	String operationId,
	TaskItemStatus sourceStatus,
	TaskItemStatus targetStatus,
	String collectorId,
	String assignmentId,
	Instant occurredAt
) { }

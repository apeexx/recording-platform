package com.recording.platform.task.store;

import java.time.Instant;
import com.recording.platform.task.model.TaskItemResult;

public record ReviewItemClaimMutation(
	String itemId,
	String reviewerId,
	String actorUsername,
	String reviewAssignmentId,
	String collectorAssignmentId,
	TaskItemResult currentResult,
	long expectedRevision,
	String operationId,
	Instant occurredAt
) { }

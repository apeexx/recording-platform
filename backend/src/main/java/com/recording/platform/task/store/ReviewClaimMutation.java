package com.recording.platform.task.store;

import java.time.Instant;

public record ReviewClaimMutation(
	String taskId,
	String reviewerId,
	String actorUsername,
	String reviewAssignmentId,
	String operationId,
	Instant occurredAt
) { }

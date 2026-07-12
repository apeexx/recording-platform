package com.recording.platform.task.store;

import java.time.Instant;

public record ReviewAssignMutation(
	String itemId,
	String reviewerId,
	String reviewerName,
	String actorUserId,
	String actorUsername,
	String reviewAssignmentId,
	long expectedRevision,
	String operationId,
	Instant occurredAt
) { }

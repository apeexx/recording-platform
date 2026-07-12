package com.recording.platform.task.store;

import java.time.Instant;

public record ReviewReleaseMutation(
	String itemId,
	String reviewerId,
	String actorUsername,
	String reviewAssignmentId,
	long expectedRevision,
	String operationId,
	Instant occurredAt
) { }

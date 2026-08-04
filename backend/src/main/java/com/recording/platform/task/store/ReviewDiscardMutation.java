package com.recording.platform.task.store;

import com.recording.platform.identity.model.UserRole;
import java.time.Instant;

public record ReviewDiscardMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	UserRole actorRole,
	long expectedRevision,
	String operationId,
	String reviewerId,
	String reviewAssignmentId,
	String collectorId,
	String assignmentId,
	String reason,
	Instant occurredAt
) { }

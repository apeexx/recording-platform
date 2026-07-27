package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.identity.model.UserRole;
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
	Instant occurredAt,
	String reason,
	UserRole actorRole
) {
	public AdminItemTransitionMutation(
		String itemId, String actorUserId, String actorUsername, long expectedRevision,
		String operationId, TaskItemStatus sourceStatus, TaskItemStatus targetStatus,
		String collectorId, String assignmentId, Instant occurredAt
	) {
		this(
			itemId, actorUserId, actorUsername, expectedRevision, operationId, sourceStatus,
			targetStatus, collectorId, assignmentId, occurredAt, null, UserRole.ADMIN
		);
	}
}

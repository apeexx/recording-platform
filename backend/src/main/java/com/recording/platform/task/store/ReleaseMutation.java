package com.recording.platform.task.store;

import java.time.Instant;

public record ReleaseMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	boolean admin,
	long expectedRevision,
	String operationId,
	Instant occurredAt
) {
}

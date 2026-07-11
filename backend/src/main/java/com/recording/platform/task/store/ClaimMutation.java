package com.recording.platform.task.store;

import java.time.Instant;

public record ClaimMutation(
	String taskId,
	String collectorId,
	String actorUsername,
	String assignmentId,
	Instant occurredAt
) {
}

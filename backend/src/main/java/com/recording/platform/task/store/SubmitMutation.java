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
	Instant occurredAt,
	Instant firstSubmittedAt,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis
) {
	public SubmitMutation(
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
		this(
			itemId, collectorId, actorUsername, assignmentId, expectedRevision, operationId,
			result, targetStatus, occurredAt, occurredAt, 0, 0
		);
	}
}

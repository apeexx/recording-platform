package com.recording.platform.task.store;

import java.time.Instant;
import com.recording.platform.task.model.TaskItemResult;

public record RejectMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	long expectedRevision,
	String operationId,
	String reason,
	String reviewedSubmissionOperationId,
	String assignmentId,
	TaskItemResult currentResult,
	Instant occurredAt
) {
}

package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record ReviewDecisionMutation(
	String itemId,
	String reviewerId,
	String actorUsername,
	String reviewAssignmentId,
	long expectedRevision,
	String operationId,
	TaskItemStatus targetStatus,
	TaskItemResult result,
	String conclusion,
	String reviewedSubmissionOperationId,
	Instant occurredAt
) { }

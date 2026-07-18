package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;
import com.recording.platform.task.model.CurrentRejection;

public record AdminReviewDecisionMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	long expectedRevision,
	String operationId,
	TaskItemStatus targetStatus,
	TaskItemResult result,
	String conclusion,
	CurrentRejection currentRejection,
	String reviewedSubmissionOperationId,
	Instant occurredAt
) { }

package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemResult;
import java.time.Instant;

public record AdminReviewApproveMutation(
	String itemId,
	String actorUserId,
	String actorUsername,
	long expectedRevision,
	String operationId,
	TaskItemResult result,
	String reviewedSubmissionOperationId,
	Instant occurredAt
) { }

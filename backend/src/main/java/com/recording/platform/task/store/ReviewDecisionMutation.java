package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;
import com.recording.platform.task.model.CurrentRejection;

public record ReviewDecisionMutation(
	String itemId,
	String reviewerId,
	String actorUsername,
	String reviewAssignmentId,
	long expectedRevision,
	String operationId,
	TaskItemStatus targetStatus,
	TaskItemResult result,
	String reviewFinalAnswer,
	String conclusion,
	CurrentRejection currentRejection,
	String reviewedSubmissionOperationId,
	Instant firstCompletedAt,
	Instant occurredAt
) {
	public ReviewDecisionMutation(
		String itemId, String reviewerId, String actorUsername, String reviewAssignmentId,
		long expectedRevision, String operationId, TaskItemStatus targetStatus, TaskItemResult result,
		String reviewFinalAnswer, String conclusion, CurrentRejection currentRejection,
		String reviewedSubmissionOperationId, Instant occurredAt
	) {
		this(
			itemId, reviewerId, actorUsername, reviewAssignmentId, expectedRevision, operationId,
			targetStatus, result, reviewFinalAnswer, conclusion, currentRejection,
			reviewedSubmissionOperationId,
			targetStatus == TaskItemStatus.COMPLETED ? occurredAt : null, occurredAt
		);
	}
}

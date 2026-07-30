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
	String reviewFinalAnswer,
	String conclusion,
	CurrentRejection currentRejection,
	String reviewedSubmissionOperationId,
	Instant firstCompletedAt,
	Instant occurredAt
) {
	public AdminReviewDecisionMutation(
		String itemId, String actorUserId, String actorUsername, long expectedRevision,
		String operationId, TaskItemStatus targetStatus, TaskItemResult result,
		String reviewFinalAnswer, String conclusion, CurrentRejection currentRejection,
		String reviewedSubmissionOperationId, Instant occurredAt
	) {
		this(
			itemId, actorUserId, actorUsername, expectedRevision, operationId, targetStatus,
			result, reviewFinalAnswer, conclusion, currentRejection, reviewedSubmissionOperationId,
			targetStatus == TaskItemStatus.COMPLETED ? occurredAt : null, occurredAt
		);
	}
}

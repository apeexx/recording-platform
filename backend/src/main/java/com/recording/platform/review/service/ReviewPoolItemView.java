package com.recording.platform.review.service;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;

public record ReviewPoolItemView(
	String id, String taskId, String itemCode, String collectorId, String collectorUserNo, String collectorName, TaskItemStatus status,
	boolean hasText, boolean hasAudio, Long audioDurationMillis, long revision,
	String reviewerId, String reviewAssignmentId
) {
	static ReviewPoolItemView from(TaskItem item, com.recording.platform.identity.model.UserAccount collector) {
		var result = item.getCurrentResult();
		return new ReviewPoolItemView(item.getId(), item.getTaskId(), item.getItemCode(), item.getCollectorId(),
			collector == null ? null : collector.getInternalUserNo(), collector == null ? null : collector.getName(),
			item.getStatus(), result != null && result.text() != null,
			result != null && result.audio() != null,
			result != null && result.audio() != null ? result.audio().durationMillis() : null,
			item.getRevision(), item.getReviewerId(), item.getReviewAssignmentId());
	}
}

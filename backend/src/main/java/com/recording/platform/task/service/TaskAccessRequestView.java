package com.recording.platform.task.service;

import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import java.time.Instant;

public record TaskAccessRequestView(
	String id, String taskId, String userId, String userNo, String userName,
	AccessRequestStatus status, Instant createdAt
) {
	static TaskAccessRequestView from(TaskAccessRequest request, com.recording.platform.identity.model.UserAccount user) {
		return new TaskAccessRequestView(request.getId(), request.getTaskId(), request.getUserId(),
			user == null ? null : user.getInternalUserNo(), user == null ? null : user.getName(),
			request.getStatus(), request.getCreatedAt());
	}
}

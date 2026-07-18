package com.recording.platform.task.service;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import java.time.Instant;

public record TaskGrantView(
	String id, String taskId, String userId, String userNo, String userName,
	GrantStatus status, Instant grantedAt
) {
	static TaskGrantView from(TaskGrant grant, com.recording.platform.identity.model.UserAccount user) {
		return new TaskGrantView(grant.getId(), grant.getTaskId(), grant.getUserId(),
			user == null ? null : user.getInternalUserNo(), user == null ? null : user.getName(),
			grant.getStatus(), grant.getCreatedAt());
	}
}

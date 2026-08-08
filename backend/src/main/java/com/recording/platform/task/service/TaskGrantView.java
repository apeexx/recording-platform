package com.recording.platform.task.service;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.identity.model.UserStatus;
import java.time.Instant;

public record TaskGrantView(
	String id, String taskId, String userId, String userName, String userLoginName,
	UserStatus userStatus, GrantStatus status, Instant grantedAt
) {
	static TaskGrantView from(TaskGrant grant, com.recording.platform.identity.model.IdentityUser user) {
		return new TaskGrantView(grant.getId(), grant.getTaskId(), grant.getUserId(),
			user == null ? null : user.name(),
			user == null ? null : user.loginName(),
			user == null ? null : user.status(),
			grant.getStatus(), grant.getCreatedAt());
	}
}

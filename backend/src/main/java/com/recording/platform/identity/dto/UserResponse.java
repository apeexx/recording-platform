package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import java.time.Instant;

public record UserResponse(
	String id,
	String internalUserNo,
	String username,
	String name,
	UserRole role,
	UserStatus status,
	boolean firstPasswordChangeRequired,
	Instant createdAt,
	Instant updatedAt
) {
	public static UserResponse from(UserAccount user) {
		return new UserResponse(
			user.getId(),
			user.getInternalUserNo(),
			user.getUsername(),
			user.getName(),
			user.getRole(),
			user.getStatus(),
			user.isFirstPasswordChangeRequired(),
			user.getCreatedAt(),
			user.getUpdatedAt()
		);
	}
}

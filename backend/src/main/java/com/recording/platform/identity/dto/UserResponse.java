package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import java.time.Instant;

public record UserResponse(
	String id,
	UserType userType,
	String loginName,
	String name,
	UserRole role,
	UserStatus status,
	boolean firstPasswordChangeRequired,
	Instant createdAt,
	Instant updatedAt,
	String invitationId,
	String invitationName,
	String invitationCodeSuffix,
	Instant invitationRedeemedAt
) {
	public static UserResponse from(WebUser user) {
		return new UserResponse(
			user.getId(),
			UserType.WEB,
			user.getUsername(),
			user.getName(),
			user.getRole(),
			user.getStatus(),
			user.isFirstPasswordChangeRequired(),
			user.getCreatedAt(),
			user.getUpdatedAt(),
			null,
			null,
			null,
			null
		);
	}

	public static UserResponse from(MiniProgramUser user) {
		return new UserResponse(user.getId(), UserType.MINIPROGRAM, user.getAccount(), user.getName(), UserRole.COLLECTOR,
			user.getStatus(), false, user.getCreatedAt(), user.getUpdatedAt(), user.getInvitationId(),
			user.getInvitationName(), user.getInvitationCodeSuffix(), user.getInvitationRedeemedAt());
	}

	public static UserResponse from(IdentityUser user) {
		return new UserResponse(user.id(), user.userType(), user.loginName(), user.name(), user.role(), user.status(),
			user.firstPasswordChangeRequired(), user.createdAt(), user.updatedAt(), null, null, null, null);
	}
}

package com.recording.platform.identity.model;

import java.time.Instant;

public record IdentityUser(
	String id,
	UserType userType,
	String loginName,
	String name,
	UserRole role,
	UserStatus status,
	boolean firstPasswordChangeRequired,
	Instant createdAt,
	Instant updatedAt
) {
	public static IdentityUser from(WebUser user) {
		return new IdentityUser(user.getId(), UserType.WEB, user.getUsername(), user.getName(), user.getRole(),
			user.getStatus(), user.isFirstPasswordChangeRequired(), user.getCreatedAt(), user.getUpdatedAt());
	}

	public static IdentityUser from(MiniProgramUser user) {
		return new IdentityUser(user.getId(), UserType.MINIPROGRAM, user.getAccount(), user.getName(), UserRole.COLLECTOR,
			user.getStatus(), false, user.getCreatedAt(), user.getUpdatedAt());
	}

}

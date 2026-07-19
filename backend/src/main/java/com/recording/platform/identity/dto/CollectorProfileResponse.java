package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.CollectorProfilePolicy;

public record CollectorProfileResponse(
	String userId,
	String internalUserNo,
	String account,
	String name,
	UserRole role,
	boolean profileComplete,
	boolean hasCustomAvatar
) {
	public static CollectorProfileResponse from(UserAccount user) {
		return new CollectorProfileResponse(
			user.getId(),
			user.getInternalUserNo(),
			user.getUsername(),
			user.getName(),
			user.getRole(),
			CollectorProfilePolicy.isComplete(user),
			user.getAvatarPath() != null && !user.getAvatarPath().isBlank()
		);
	}
}

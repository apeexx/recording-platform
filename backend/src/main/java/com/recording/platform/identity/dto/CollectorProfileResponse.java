package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.CollectorProfilePolicy;

public record CollectorProfileResponse(
	String userId,
	String account,
	String name,
	UserRole role,
	boolean profileComplete,
	boolean hasCustomAvatar
) {
	public static CollectorProfileResponse from(MiniProgramUser user) {
		return new CollectorProfileResponse(
			user.getId(),
			user.getAccount(),
			user.getName(),
			UserRole.COLLECTOR,
			CollectorProfilePolicy.isComplete(user),
			user.getAvatarPath() != null && !user.getAvatarPath().isBlank()
		);
	}
}

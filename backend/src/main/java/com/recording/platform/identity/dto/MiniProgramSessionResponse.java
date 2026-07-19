package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.MiniProgramLoginResult;

public record MiniProgramSessionResponse(
	String token,
	String userId,
	String internalUserNo,
	String account,
	String name,
	UserRole role,
	boolean profileComplete,
	boolean hasCustomAvatar
) {
	public static MiniProgramSessionResponse from(MiniProgramLoginResult result) {
		UserAccount user = result.user();
		return new MiniProgramSessionResponse(
			result.token(),
			user.getId(),
			user.getInternalUserNo(),
			user.getUsername(),
			user.getName(),
			user.getRole(),
			com.recording.platform.identity.service.CollectorProfilePolicy.isComplete(user),
			user.getAvatarPath() != null && !user.getAvatarPath().isBlank()
		);
	}
}

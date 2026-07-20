package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.MiniProgramLoginResult;

public record MiniProgramSessionResponse(
	String token,
	String userId,
	String account,
	String name,
	UserRole role,
	boolean profileComplete,
	boolean hasCustomAvatar
) {
	public static MiniProgramSessionResponse from(MiniProgramLoginResult result) {
		MiniProgramUser user = result.user();
		return new MiniProgramSessionResponse(
			result.token(),
			user.getId(),
			user.getAccount(),
			user.getName(),
			UserRole.COLLECTOR,
			com.recording.platform.identity.service.CollectorProfilePolicy.isComplete(user),
			user.getAvatarPath() != null && !user.getAvatarPath().isBlank()
		);
	}
}

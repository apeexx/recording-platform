package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.MiniProgramLoginResult;

public record MiniProgramSessionResponse(
	String token,
	String userId,
	String internalUserNo,
	String name,
	UserRole role
) {
	public static MiniProgramSessionResponse from(MiniProgramLoginResult result) {
		UserAccount user = result.user();
		return new MiniProgramSessionResponse(
			result.token(),
			user.getId(),
			user.getInternalUserNo(),
			user.getName(),
			user.getRole()
		);
	}
}

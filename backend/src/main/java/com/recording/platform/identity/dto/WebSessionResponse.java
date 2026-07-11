package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.WebLoginResult;
import com.recording.platform.security.PlatformPrincipal;

public record WebSessionResponse(
	String userId,
	String username,
	String name,
	UserRole role,
	boolean firstPasswordChangeRequired
) {
	public static WebSessionResponse from(WebLoginResult result) {
		return new WebSessionResponse(
			result.userId(),
			result.username(),
			result.name(),
			result.role(),
			result.firstPasswordChangeRequired()
		);
	}

	public static WebSessionResponse from(PlatformPrincipal principal) {
		return new WebSessionResponse(
			principal.userId(),
			principal.username(),
			principal.name(),
			principal.role(),
			principal.firstPasswordChangeRequired()
		);
	}
}

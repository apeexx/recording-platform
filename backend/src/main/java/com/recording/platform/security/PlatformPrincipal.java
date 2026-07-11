package com.recording.platform.security;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.SessionIdentity;

public record PlatformPrincipal(
	String sessionId,
	String userId,
	String username,
	String name,
	UserRole role,
	SessionType sessionType,
	boolean firstPasswordChangeRequired
) {
	public static PlatformPrincipal from(SessionIdentity identity) {
		return new PlatformPrincipal(
			identity.sessionId(),
			identity.userId(),
			identity.username(),
			identity.name(),
			identity.role(),
			identity.sessionType(),
			identity.firstPasswordChangeRequired()
		);
	}
}

package com.recording.platform.identity.service;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;

public record SessionIdentity(
	String sessionId,
	String userId,
	String username,
	String name,
	UserRole role,
	SessionType sessionType,
	boolean firstPasswordChangeRequired
) {
}

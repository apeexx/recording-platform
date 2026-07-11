package com.recording.platform.identity.service;

import com.recording.platform.identity.model.UserRole;

public record WebLoginResult(
	String token,
	String sessionId,
	String userId,
	String username,
	String name,
	UserRole role,
	boolean firstPasswordChangeRequired
) {
}

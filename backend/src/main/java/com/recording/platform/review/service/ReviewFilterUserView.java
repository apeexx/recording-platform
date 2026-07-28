package com.recording.platform.review.service;

import com.recording.platform.identity.model.IdentityUser;

public record ReviewFilterUserView(String id, String name, String loginName) {
	public static ReviewFilterUserView from(IdentityUser user) {
		return new ReviewFilterUserView(user.id(), user.name(), user.loginName());
	}
}

package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.security.PlatformPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CollectorProfileGuard {
	private final UserStore users;
	public CollectorProfileGuard(UserStore users) { this.users = users; }

	public void requireComplete(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.COLLECTOR) return;
		var user = users.findById(actor.userId()).orElseThrow(() -> incomplete());
		if (!CollectorProfilePolicy.isComplete(user)) throw incomplete();
	}

	private ApiException incomplete() {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROFILE_INCOMPLETE", "请先完成姓名、登录账号和密码设置");
	}
}

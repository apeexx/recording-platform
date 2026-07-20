package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.controller.MiniProgramAuthenticationController;
import com.recording.platform.identity.dto.CollectorAccountLoginRequest;
import com.recording.platform.identity.dto.CompleteCollectorProfileRequest;
import com.recording.platform.identity.dto.UpdateCollectorPasswordRequest;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.CollectorIdentityService;
import com.recording.platform.identity.service.MiniProgramLoginResult;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.security.PlatformPrincipal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MiniProgramAuthenticationControllerTests {
	@Test
	void exposesAccountLoginAndProfileLifecycleThroughMiniProgramResponses() {
		WeChatAuthenticationService wechat = org.mockito.Mockito.mock(WeChatAuthenticationService.class);
		CollectorIdentityService collectors = org.mockito.Mockito.mock(CollectorIdentityService.class);
		MiniProgramUser user = collector();
		when(collectors.login("682913", "Password-1"))
			.thenReturn(new MiniProgramLoginResult("opaque", "session-1", user));
		when(collectors.profile("collector-1")).thenReturn(user);
		when(collectors.completeProfile("collector-1", "张三", "682913", "Password-1")).thenReturn(user);
		when(collectors.changePassword("collector-1", "Password-1", "Password-2")).thenReturn(user);
		MiniProgramAuthenticationController controller = new MiniProgramAuthenticationController(wechat, collectors);
		PlatformPrincipal principal = new PlatformPrincipal(
			"session-1", "collector-1", "682913", "张三", UserRole.COLLECTOR,
			SessionType.MINIPROGRAM, false
		);

		assertThat(controller.accountLogin(new CollectorAccountLoginRequest("682913", "Password-1")).userId())
			.isEqualTo("collector-1");
		assertThat(controller.profile(principal).profileComplete()).isTrue();
		assertThat(controller.completeProfile(
			principal, new CompleteCollectorProfileRequest("张三", "682913", "Password-1")
		).account()).isEqualTo("682913");
		assertThat(controller.changePassword(
			principal, new UpdateCollectorPasswordRequest("Password-1", "Password-2")
		).profileComplete()).isTrue();
	}

	private MiniProgramUser collector() {
		MiniProgramUser user = new MiniProgramUser();
		user.setId("collector-1");
		user.setAccount("682913");
		user.setName("张三");
		user.setPasswordHash("bcrypt-hash");
		user.setStatus(UserStatus.ACTIVE);
		user.setCreatedAt(Instant.parse("2026-07-19T08:00:00Z"));
		user.setUpdatedAt(Instant.parse("2026-07-19T08:00:00Z"));
		return user;
	}
}

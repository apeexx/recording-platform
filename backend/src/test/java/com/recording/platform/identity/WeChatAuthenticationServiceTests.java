package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.dto.WeChatLoginRequest;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.MiniProgramLoginResult;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.identity.store.SessionStore;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.identity.wechat.DefaultWeChatClient;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatIdentity;
import com.recording.platform.identity.wechat.WeChatSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeChatAuthenticationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void missingAppSecretReturnsAConfigurationErrorBeforeCallingWechat() {
		DefaultWeChatClient client = new DefaultWeChatClient(new WeChatSettings("test-app-id", ""));

		assertThatThrownBy(() -> client.exchange("temporary-code"))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(503);
				assertThat(exception.getCode()).isEqualTo("WECHAT_NOT_CONFIGURED");
			});
	}

	@Test
	void loginUsesServerSideCodeExchangeAndCreatesACollectorBoundToAppIdAndOpenId() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		SessionStore sessions = org.mockito.Mockito.mock(SessionStore.class);
		when(users.findByWechatIdentity("test-app-id", "openid-from-wechat")).thenReturn(Optional.empty());
		when(users.save(any())).thenAnswer((invocation) -> {
			UserAccount user = invocation.getArgument(0);
			if (user.getId() == null) {
				user.setId("collector-1");
			}
			return user;
		});
		when(sessions.save(any())).thenAnswer((invocation) -> {
			SessionRecord session = invocation.getArgument(0);
			session.setId(UUID.randomUUID().toString());
			return session;
		});
		WeChatClient weChat = (code) -> {
			assertThat(code).isEqualTo("temporary-code");
			return new WeChatIdentity("test-app-id", "openid-from-wechat");
		};
		SessionService sessionService = new SessionService(
			sessions,
			users,
			new OpaqueTokenService(),
			CLOCK,
			Duration.ofHours(12),
			Duration.ofDays(30)
		);
		WeChatAuthenticationService service = new WeChatAuthenticationService(users, sessionService, weChat, CLOCK);

		MiniProgramLoginResult result = service.login("temporary-code");

		assertThat(result.token()).isNotBlank();
		assertThat(result.user().getRole()).isEqualTo(UserRole.COLLECTOR);
		assertThat(result.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(result.user().getWechatAppId()).isEqualTo("test-app-id");
		assertThat(result.user().getWechatOpenId()).isEqualTo("openid-from-wechat");
		assertThat(result.user().getInternalUserNo()).startsWith("USR-");
	}

	@Test
	void clientRequestContractOnlyAcceptsTheTemporaryCodeNotOpenId() {
		assertThat(Arrays.stream(WeChatLoginRequest.class.getRecordComponents())
			.map((component) -> component.getName())
			.toList())
			.containsExactly("code");
	}
}

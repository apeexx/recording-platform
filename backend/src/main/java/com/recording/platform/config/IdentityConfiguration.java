package com.recording.platform.config;

import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.SessionStore;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.wechat.DefaultWeChatClient;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatSettings;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfiguration {
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SessionService sessionService(
		SessionStore sessions,
		IdentityDirectory identities,
		OpaqueTokenService tokens,
		Clock clock,
		@Value("${recording.web-session.idle-hours:12}") long webIdleHours,
		@Value("${recording.miniprogram-session.days:30}") long miniProgramDays
	) {
		return new SessionService(
			sessions,
			identities,
			tokens,
			clock,
			Duration.ofHours(Math.max(webIdleHours, 1)),
			Duration.ofDays(Math.max(miniProgramDays, 1))
		);
	}

	@Bean
	WeChatSettings weChatSettings(
		@Value("${recording.wechat.app-id:}") String appId,
		@Value("${recording.wechat.app-secret:}") String appSecret
	) {
		return new WeChatSettings(appId, appSecret);
	}

	@Bean
	WeChatClient weChatClient(WeChatSettings settings) {
		return new DefaultWeChatClient(settings);
	}
}

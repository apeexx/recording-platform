package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.IssuedSession;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class WeChatAuthenticationServiceTests {
	@Test
	void createsMiniProgramUserWithPrefixedIdAndNoRoleField() {
		MiniProgramUserStore users=mock(MiniProgramUserStore.class);SessionService sessions=mock(SessionService.class);WeChatClient client=mock(WeChatClient.class);
		when(client.exchange("code")).thenReturn(new WeChatIdentity("app", "openid")); when(users.findByWechatIdentity("app","openid")).thenReturn(Optional.empty());
		when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0)); when(sessions.active(any(), org.mockito.ArgumentMatchers.eq(SessionType.MINIPROGRAM))).thenReturn(Optional.empty());
		when(sessions.issueMiniProgram(any())).thenAnswer(invocation -> { SessionRecord record=new SessionRecord();record.setId("session");record.setUserId(invocation.getArgument(0));return new IssuedSession("token",record); });
		var result=new WeChatAuthenticationService(users,sessions,client,Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"),ZoneOffset.UTC)).login("code");
		assertThat(result.user().getId()).matches("MINI-[0-9a-f]{24}"); assertThat(result.user().getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(java.util.Arrays.stream(MiniProgramUser.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)).doesNotContain("role");
	}

	@Test void existingWechatIdentityIsReusedWithoutCreatingAnotherUser(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);SessionService sessions=mock(SessionService.class);WeChatClient client=mock(WeChatClient.class);MiniProgramUser user=new MiniProgramUser();user.setId("MINI-0123456789abcdef01234567");user.setStatus(UserStatus.ACTIVE);
		when(client.exchange("code")).thenReturn(new WeChatIdentity("app","openid"));when(users.findByWechatIdentity("app","openid")).thenReturn(Optional.of(user));when(sessions.active(user.getId(),SessionType.MINIPROGRAM)).thenReturn(Optional.empty());SessionRecord record=new SessionRecord();record.setId("session");record.setUserId(user.getId());when(sessions.issueMiniProgram(user.getId())).thenReturn(new IssuedSession("token",record));
		assertThat(new WeChatAuthenticationService(users,sessions,client,Clock.systemUTC()).login("code").user().getId()).isEqualTo(user.getId());verify(users,never()).save(any());}

	@Test
	void concurrentFirstLoginReusesWinnerAndReturnsMiniProgramTakeover() {
		MiniProgramUserStore users = mock(MiniProgramUserStore.class);
		SessionService sessions = mock(SessionService.class);
		WeChatClient client = mock(WeChatClient.class);
		MiniProgramUser winner = new MiniProgramUser();
		winner.setId("MINI-0123456789abcdef01234567");
		winner.setStatus(UserStatus.ACTIVE);
		SessionRecord active = new SessionRecord();
		active.setId("active-mini");
		SessionRecord takeover = new SessionRecord();
		takeover.setId("takeover-mini");

		when(client.exchange("code")).thenReturn(new WeChatIdentity("app", "openid"));
		when(users.findByWechatIdentity("app", "openid")).thenReturn(Optional.empty(), Optional.of(winner));
		when(users.save(any())).thenThrow(new DuplicateKeyException("unique_wechat_identity"));
		when(sessions.active(winner.getId(), SessionType.MINIPROGRAM)).thenReturn(Optional.of(active));
		when(sessions.issueMiniProgramTakeover(winner.getId(), active.getId()))
			.thenReturn(new IssuedSession("takeover-token", takeover));

		assertThatThrownBy(() -> new WeChatAuthenticationService(users, sessions, client, Clock.systemUTC()).login("code"))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.getCode()).isEqualTo("ACCOUNT_IN_USE");
				assertThat(exception.getDetails()).containsEntry("takeoverToken", "takeover-token");
			});
	}
}

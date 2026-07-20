package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.controller.MiniProgramAuthenticationController;
import com.recording.platform.identity.dto.TakeoverRequest;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.service.CollectorIdentityService;
import com.recording.platform.identity.service.IssuedSession;
import com.recording.platform.identity.service.MiniProgramLoginResult;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

class SplitAuthenticationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneOffset.UTC);
	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

	@Test
	void identicalLoginNamesCanExistAcrossWebAndMiniProgramCollections() {
		WebUserStore webUsers = mock(WebUserStore.class);
		MiniProgramUserStore miniUsers = mock(MiniProgramUserStore.class);
		SessionService sessions = mock(SessionService.class);
		WebUser web = web("WEB-0123456789abcdef01234567", "682913");
		MiniProgramUser mini = mini("MINI-0123456789abcdef01234567", "682913");
		when(webUsers.findByUsername("682913")).thenReturn(Optional.of(web));
		when(miniUsers.findByAccount("682913")).thenReturn(Optional.of(mini));
		when(sessions.active(web.getId(), SessionType.WEB)).thenReturn(Optional.empty());
		when(sessions.active(mini.getId(), SessionType.MINIPROGRAM)).thenReturn(Optional.empty());
		when(sessions.issueWeb(web.getId())).thenReturn(issued("web-token", web.getId(), SessionType.WEB));
		when(sessions.issueMiniProgram(mini.getId())).thenReturn(issued("mini-token", mini.getId(), SessionType.MINIPROGRAM));

		var webLogin = new WebAuthenticationService(webUsers, sessions, passwords, CLOCK)
			.login("682913", "Password-1");
		var miniLogin = new CollectorIdentityService(miniUsers, sessions, passwords, CLOCK)
			.login("682913", "Password-1");

		assertThat(webLogin.userId()).startsWith("WEB-");
		assertThat(miniLogin.user().getId()).startsWith("MINI-");
	}

	@Test
	void numericAndWechatLoginBothReturnMiniProgramTakeoverConflicts() {
		MiniProgramUserStore miniUsers = mock(MiniProgramUserStore.class);
		SessionService sessions = mock(SessionService.class);
		MiniProgramUser mini = mini("MINI-0123456789abcdef01234567", "682913");
		SessionRecord active = session("active", mini.getId(), SessionType.MINIPROGRAM);
		when(miniUsers.findByAccount("682913")).thenReturn(Optional.of(mini));
		when(miniUsers.findByWechatIdentity("wx-app", "openid")).thenReturn(Optional.of(mini));
		when(sessions.active(mini.getId(), SessionType.MINIPROGRAM)).thenReturn(Optional.of(active));
		when(sessions.issueMiniProgramTakeover(mini.getId(), active.getId()))
			.thenReturn(issued("mini-takeover", mini.getId(), SessionType.MINIPROGRAM_TAKEOVER));
		CollectorIdentityService collectors = new CollectorIdentityService(miniUsers, sessions, passwords, CLOCK);
		WeChatClient weChat = mock(WeChatClient.class);
		when(weChat.exchange("code")).thenReturn(new WeChatIdentity("wx-app", "openid"));
		WeChatAuthenticationService weChatAuthentication = new WeChatAuthenticationService(miniUsers, sessions, weChat, CLOCK);

		assertConflict(() -> collectors.login("682913", "Password-1"));
		assertConflict(() -> weChatAuthentication.login("code"));
		verify(sessions, org.mockito.Mockito.times(2)).issueMiniProgramTakeover(mini.getId(), active.getId());
	}

	@Test
	void miniProgramControllerExposesTakeoverAndReturnsTheCollectorSessionShape() {
		CollectorIdentityService collectors = mock(CollectorIdentityService.class);
		WeChatAuthenticationService weChat = mock(WeChatAuthenticationService.class);
		MiniProgramUser mini = mini("MINI-0123456789abcdef01234567", "682913");
		when(collectors.takeover("takeover-token"))
			.thenReturn(new MiniProgramLoginResult("new-token", "new-session", mini));
		MiniProgramAuthenticationController controller = new MiniProgramAuthenticationController(weChat, collectors);

		var response = controller.takeover(new TakeoverRequest("takeover-token"));

		assertThat(response.token()).isEqualTo("new-token");
		assertThat(response.account()).isEqualTo("682913");
		assertThat(response.role()).isEqualTo(UserRole.COLLECTOR);
	}

	@Test
	void concurrentUniqueIndexConflictsStillReturnTypeSpecificTakeoverTokens() {
		WebUserStore webUsers=mock(WebUserStore.class);MiniProgramUserStore miniUsers=mock(MiniProgramUserStore.class);SessionService sessions=mock(SessionService.class);
		WebUser web=web("WEB-0123456789abcdef01234567","admin");MiniProgramUser mini=mini("MINI-0123456789abcdef01234567","682913");
		SessionRecord webActive=session("web-active",web.getId(),SessionType.WEB);SessionRecord miniActive=session("mini-active",mini.getId(),SessionType.MINIPROGRAM);
		when(webUsers.findByUsername("admin")).thenReturn(Optional.of(web));when(miniUsers.findByAccount("682913")).thenReturn(Optional.of(mini));
		when(sessions.active(web.getId(),SessionType.WEB)).thenReturn(Optional.empty(),Optional.of(webActive));when(sessions.issueWeb(web.getId())).thenThrow(new DuplicateKeyException("unique_active_web_session"));
		when(sessions.issueWebTakeover(web.getId(),webActive.getId())).thenReturn(issued("web-takeover",web.getId(),SessionType.WEB_TAKEOVER));
		when(sessions.active(mini.getId(),SessionType.MINIPROGRAM)).thenReturn(Optional.empty(),Optional.of(miniActive));when(sessions.issueMiniProgram(mini.getId())).thenThrow(new DuplicateKeyException("unique_active_miniprogram_session"));
		when(sessions.issueMiniProgramTakeover(mini.getId(),miniActive.getId())).thenReturn(issued("mini-takeover",mini.getId(),SessionType.MINIPROGRAM_TAKEOVER));
		assertThatThrownBy(()->new WebAuthenticationService(webUsers,sessions,passwords,CLOCK).login("admin","Password-1"))
			.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getDetails()).containsEntry("takeoverToken","web-takeover"));
		assertThatThrownBy(()->new CollectorIdentityService(miniUsers,sessions,passwords,CLOCK).login("682913","Password-1"))
			.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getDetails()).containsEntry("takeoverToken","mini-takeover"));
	}

	private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class, exception -> {
			assertThat(exception.getCode()).isEqualTo("ACCOUNT_IN_USE");
			assertThat(exception.getDetails()).containsEntry("takeoverToken", "mini-takeover");
		});
	}

	private WebUser web(String id, String username) {
		WebUser user = new WebUser(); user.setId(id); user.setUsername(username); user.setName("管理员");
		user.setPasswordHash(passwords.encode("Password-1")); user.setRole(UserRole.ADMIN); user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private MiniProgramUser mini(String id, String account) {
		MiniProgramUser user = new MiniProgramUser(); user.setId(id); user.setAccount(account); user.setName("录音员");
		user.setPasswordHash(passwords.encode("Password-1")); user.setStatus(UserStatus.ACTIVE); return user;
	}

	private IssuedSession issued(String token, String userId, SessionType type) {
		return new IssuedSession(token, session("session-" + token, userId, type));
	}

	private SessionRecord session(String id, String userId, SessionType type) {
		SessionRecord session = new SessionRecord(); session.setId(id); session.setUserId(userId); session.setType(type);
		return session;
	}
}

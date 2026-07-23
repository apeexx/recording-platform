package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.service.IssuedSession;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class WebAuthenticationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneOffset.UTC);
	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

	@Test
	void passwordChangeUsesCasAndRevokesAllSessions() {
		WebUserStore users = mock(WebUserStore.class); SessionService sessions = mock(SessionService.class);
		WebUser user = user(); when(users.findById(user.getId())).thenReturn(Optional.of(user));
		when(users.updatePasswordIfActive(eq(user.getId()), eq(user.getPasswordHash()), any(), eq(Instant.now(CLOCK)))).thenReturn(true);
		WebAuthenticationService service = new WebAuthenticationService(users, sessions, passwords, CLOCK);

		service.changePassword(user.getId(), "Password-1", "Password-2");

		verify(sessions).revokeAll(user.getId());
		verify(users).updatePasswordIfActive(eq(user.getId()), eq(user.getPasswordHash()),
			org.mockito.ArgumentMatchers.argThat(hash -> passwords.matches("Password-2", hash)), eq(Instant.now(CLOCK)));
	}

	@Test
	void initialPasswordChangeDoesNotRequireCurrentPasswordAndRevokesAllSessions() {
		WebUserStore users = mock(WebUserStore.class); SessionService sessions = mock(SessionService.class);
		WebUser user = user(); when(users.findById(user.getId())).thenReturn(Optional.of(user));
		when(users.updateInitialPasswordIfRequired(eq(user.getId()), any(), eq(Instant.now(CLOCK)))).thenReturn(true);
		WebAuthenticationService service = new WebAuthenticationService(users, sessions, passwords, CLOCK);

		service.changeInitialPassword(user.getId(), "Password-2");

		verify(sessions).revokeAll(user.getId());
		verify(users).updateInitialPasswordIfRequired(eq(user.getId()),
			org.mockito.ArgumentMatchers.argThat(hash -> passwords.matches("Password-2", hash)), eq(Instant.now(CLOCK)));
	}

	@Test
	void skippingInitialPasswordChangeClearsFlagWithoutRevokingSession() {
		WebUserStore users = mock(WebUserStore.class); SessionService sessions = mock(SessionService.class);
		WebUser user = user(); when(users.findById(user.getId())).thenReturn(Optional.of(user));
		when(users.clearInitialPasswordChangeIfRequired(user.getId(), Instant.now(CLOCK))).thenReturn(true);
		WebAuthenticationService service = new WebAuthenticationService(users, sessions, passwords, CLOCK);

		service.skipInitialPasswordChange(user.getId());

		verify(users).clearInitialPasswordChangeIfRequired(user.getId(), Instant.now(CLOCK));
		verify(sessions, org.mockito.Mockito.never()).revokeAll(any());
	}

	@Test
	void invalidOrOversizedPasswordsKeepExistingErrorContracts() {
		WebUserStore users = mock(WebUserStore.class); WebUser user = user(); when(users.findById(user.getId())).thenReturn(Optional.of(user));
		WebAuthenticationService service = new WebAuthenticationService(users, mock(SessionService.class), passwords, CLOCK);
		assertThatThrownBy(() -> service.changePassword(user.getId(), "wrong", "Password-2"))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS"));
		assertThatThrownBy(() -> service.changePassword(user.getId(), "Password-1", "x".repeat(73)))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("PASSWORD_TOO_WEAK"));
	}

	@Test void invalidLoginPasswordDoesNotIssueASession(){WebUserStore users=mock(WebUserStore.class);SessionService sessions=mock(SessionService.class);WebUser user=user();when(users.findByUsername("admin")).thenReturn(Optional.of(user));WebAuthenticationService service=new WebAuthenticationService(users,sessions,passwords,CLOCK);
		assertThatThrownBy(()->service.login("admin","wrong")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));verify(sessions,org.mockito.Mockito.never()).issueWeb(any());}

	@Test void activeLoginReturnsTakeoverAndLogoutRevokesCurrentSession(){WebUserStore users=mock(WebUserStore.class);SessionService sessions=mock(SessionService.class);WebUser user=user();SessionRecord active=new SessionRecord();active.setId("active");when(users.findByUsername("admin")).thenReturn(Optional.of(user));when(sessions.active(user.getId(),SessionType.WEB)).thenReturn(Optional.of(active));SessionRecord takeover=new SessionRecord();takeover.setId("takeover");when(sessions.issueWebTakeover(user.getId(),"active")).thenReturn(new IssuedSession("takeover-token",takeover));WebAuthenticationService service=new WebAuthenticationService(users,sessions,passwords,CLOCK);
		assertThatThrownBy(()->service.login("admin","Password-1")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getDetails()).containsEntry("takeoverToken","takeover-token"));service.logout("active");verify(sessions).revoke("active");}

	@Test
	void validLoginPreservesFirstPasswordFlagAndTakeoverAndLogoutChangeAuthenticationState() {
		WebUser user = user();
		WebUserStore users = mock(WebUserStore.class);
		when(users.findByUsername("admin")).thenReturn(Optional.of(user));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
		IdentityDirectory identities = mock(IdentityDirectory.class);
		when(identities.findById(user.getId())).thenReturn(Optional.of(new IdentityUser(
			user.getId(), UserType.WEB, user.getUsername(), user.getName(), user.getRole(), user.getStatus(),
			user.isFirstPasswordChangeRequired(), user.getCreatedAt(), user.getUpdatedAt()
		)));
		SessionService sessions = new SessionService(new InMemorySessionStore(), identities, new OpaqueTokenService(),
			CLOCK, Duration.ofHours(12), Duration.ofDays(30));
		WebAuthenticationService service = new WebAuthenticationService(users, sessions, passwords, CLOCK);

		var first = service.login("admin", "Password-1");
		assertThat(first.firstPasswordChangeRequired()).isTrue();
		ApiException conflict = assertThrows(ApiException.class, () -> service.login("admin", "Password-1"));
		String takeoverToken = (String) conflict.getDetails().get("takeoverToken");
		var replacement = service.takeover(takeoverToken);
		assertThatThrownBy(() -> sessions.authenticateWeb(first.token()))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("SESSION_REPLACED"));
		assertThat(sessions.authenticateWeb(replacement.token()).userId()).isEqualTo(user.getId());

		service.logout(replacement.sessionId());
		assertThatThrownBy(() -> sessions.authenticateWeb(replacement.token()))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("SESSION_INVALID"));
	}

	private WebUser user() {
		WebUser user = new WebUser(); user.setId("WEB-0123456789abcdef01234567"); user.setUsername("admin");
		user.setName("管理员"); user.setRole(UserRole.ADMIN); user.setStatus(UserStatus.ACTIVE);
		user.setPasswordHash(passwords.encode("Password-1")); user.setFirstPasswordChangeRequired(true); return user;
	}
}

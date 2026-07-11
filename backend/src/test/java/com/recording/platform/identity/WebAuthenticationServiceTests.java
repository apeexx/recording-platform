package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.IssuedSession;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.service.WebLoginResult;
import com.recording.platform.identity.store.SessionStore;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import static org.mockito.Mockito.when;

class WebAuthenticationServiceTests {
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private InMemoryUserStore users;
	private InMemorySessionStore sessions;
	private SessionService sessionService;
	private WebAuthenticationService authentication;
	private UserAccount admin;

	@BeforeEach
	void setUp() {
		users = new InMemoryUserStore();
		sessions = new InMemorySessionStore();
		sessionService = new SessionService(
			sessions,
			users,
			new OpaqueTokenService(),
			clock,
			Duration.ofHours(12),
			Duration.ofDays(30)
		);
		authentication = new WebAuthenticationService(users, sessionService, passwordEncoder, clock);
		admin = activeUser("admin-1", "admin", "InitialPassword-1", UserRole.ADMIN, true);
		users.save(admin);
	}

	@Test
	void validatesBcryptPasswordAndExposesFirstPasswordChangeRequirement() {
		WebLoginResult login = authentication.login("admin", "InitialPassword-1");

		assertThat(login.token()).isNotBlank();
		assertThat(login.firstPasswordChangeRequired()).isTrue();
		assertThat(sessionService.authenticateWeb(login.token()).userId()).isEqualTo("admin-1");
		assertThatThrownBy(() -> authentication.login("admin", "wrong-password"))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(401);
				assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
			});
	}

	@Test
	void oversizedLoginPasswordIsRejectedAsInvalidCredentials() {
		assertThatThrownBy(() -> authentication.login("admin", "x".repeat(73)))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(401);
				assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
			});
	}

	@Test
	void activeLoginRequiresOneTimeTakeoverAndReplacesTheOldSession() {
		WebLoginResult firstLogin = authentication.login("admin", "InitialPassword-1");

		ApiException conflict = org.assertj.core.api.Assertions.catchThrowableOfType(
			() -> authentication.login("admin", "InitialPassword-1"),
			ApiException.class
		);
		assertThat(conflict.getStatus().value()).isEqualTo(409);
		assertThat(conflict.getCode()).isEqualTo("ACCOUNT_IN_USE");
		String takeoverToken = (String) conflict.getDetails().get("takeoverToken");
		assertThat(takeoverToken).isNotBlank();

		WebLoginResult replacement = authentication.takeover(takeoverToken);
		assertThat(replacement.token()).isNotEqualTo(firstLogin.token());
		assertThatThrownBy(() -> sessionService.authenticateWeb(firstLogin.token()))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("SESSION_REPLACED")
			);
		assertThat(sessionService.authenticateWeb(replacement.token()).userId()).isEqualTo("admin-1");
		assertThatThrownBy(() -> authentication.takeover(takeoverToken))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("TAKEOVER_TOKEN_INVALID")
			);
	}

	@Test
	void logoutRevokesTheCurrentSession() {
		WebLoginResult login = authentication.login("admin", "InitialPassword-1");

		authentication.logout(login.sessionId());

		assertThatThrownBy(() -> sessionService.authenticateWeb(login.token()))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("SESSION_INVALID")
			);
	}

	@Test
	void firstPasswordChangeUsesBcryptAndRevokesEveryActiveSession() {
		WebLoginResult login = authentication.login("admin", "InitialPassword-1");

		authentication.changePassword("admin-1", "InitialPassword-1", "ChangedPassword-2");

		assertThat(admin.isFirstPasswordChangeRequired()).isFalse();
		assertThat(passwordEncoder.matches("ChangedPassword-2", admin.getPasswordHash())).isTrue();
		assertThatThrownBy(() -> sessionService.authenticateWeb(login.token()))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("SESSION_INVALID")
			);
	}

	@Test
	void passwordLongerThanTheSupportedLimitUsesTheWeakPasswordContract() {
		assertThatThrownBy(() -> authentication.changePassword(
			"admin-1",
			"InitialPassword-1",
			"x".repeat(129)
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("PASSWORD_TOO_WEAK");
		});
	}

	@Test
	void oversizedCurrentPasswordIsRejectedAsInvalidCredentials() {
		assertThatThrownBy(() -> authentication.changePassword(
			"admin-1",
			"x".repeat(73),
			"ChangedPassword-2"
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(401);
			assertThat(exception.getCode()).isEqualTo("INVALID_CREDENTIALS");
		});
	}

	@Test
	void concurrentLoginUniqueIndexConflictStillReturnsAccountInUseWithTakeoverToken() {
		SessionService concurrentSessions = org.mockito.Mockito.mock(SessionService.class);
		SessionRecord active = new SessionRecord();
		active.setId("active-session");
		SessionRecord takeover = new SessionRecord();
		takeover.setId("takeover-session");
		when(concurrentSessions.activeWeb("admin-1"))
			.thenReturn(Optional.empty(), Optional.of(active));
		when(concurrentSessions.issueWeb("admin-1"))
			.thenThrow(new DuplicateKeyException("unique_active_web_session"));
		when(concurrentSessions.issueTakeover("admin-1", "active-session"))
			.thenReturn(new IssuedSession("takeover-token", takeover));
		WebAuthenticationService concurrentAuthentication = new WebAuthenticationService(
			users,
			concurrentSessions,
			passwordEncoder,
			clock
		);

		assertThatThrownBy(() -> concurrentAuthentication.login("admin", "InitialPassword-1"))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getCode()).isEqualTo("ACCOUNT_IN_USE");
				assertThat(exception.getDetails().get("takeoverToken")).isEqualTo("takeover-token");
			});
	}

	private UserAccount activeUser(
		String id,
		String username,
		String password,
		UserRole role,
		boolean firstPasswordChangeRequired
	) {
		UserAccount user = new UserAccount();
		user.setId(id);
		user.setInternalUserNo("USR-" + id);
		user.setUsername(username);
		user.setName(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(role);
		user.setStatus(UserStatus.ACTIVE);
		user.setFirstPasswordChangeRequired(firstPasswordChangeRequired);
		user.setCreatedAt(Instant.now(clock));
		user.setUpdatedAt(Instant.now(clock));
		return user;
	}

	private static final class InMemoryUserStore implements UserStore {
		private final List<UserAccount> values = new ArrayList<>();

		@Override
		public UserAccount save(UserAccount user) {
			values.removeIf((existing) -> existing.getId().equals(user.getId()));
			values.add(user);
			return user;
		}

		@Override
		public Optional<UserAccount> findById(String id) {
			return values.stream().filter((user) -> user.getId().equals(id)).findFirst();
		}

		@Override
		public Optional<UserAccount> findByUsername(String username) {
			return values.stream().filter((user) -> username.equals(user.getUsername())).findFirst();
		}

		@Override
		public Optional<UserAccount> findByWechatIdentity(String appId, String openId) {
			return Optional.empty();
		}

		@Override
		public boolean existsByRole(UserRole role) {
			return values.stream().anyMatch((user) -> user.getRole() == role);
		}

		@Override
		public Page<UserAccount> findAll(Pageable pageable) {
			return new PageImpl<>(values, pageable, values.size());
		}

		@Override
		public Page<UserAccount> findAllBackend(Pageable pageable) {
			return new PageImpl<>(values.stream()
				.filter((user) -> user.getRole() != UserRole.COLLECTOR)
				.toList(), pageable, values.size());
		}

		@Override
		public Optional<UserAccount> disableBackendIfActive(String userId, Instant updatedAt) {
			return findById(userId).filter((user) -> user.getRole() != UserRole.COLLECTOR)
				.filter((user) -> user.getStatus() == UserStatus.ACTIVE)
				.map((user) -> {
					user.setStatus(UserStatus.DISABLED);
					user.setUpdatedAt(updatedAt);
					return user;
				});
		}

		@Override
		public boolean updatePasswordIfActive(
			String userId,
			String expectedPasswordHash,
			String passwordHash,
			Instant updatedAt
		) {
			Optional<UserAccount> user = findById(userId).filter((value) ->
				value.getStatus() == UserStatus.ACTIVE && expectedPasswordHash.equals(value.getPasswordHash())
			);
			if (user.isEmpty()) {
				return false;
			}
			user.get().setPasswordHash(passwordHash);
			user.get().setFirstPasswordChangeRequired(false);
			user.get().setUpdatedAt(updatedAt);
			return true;
		}

		@Override
		public Optional<UserAccount> updateCollectorNameIfActive(String userId, String name, Instant updatedAt) {
			return findById(userId)
				.filter((user) -> user.getRole() == UserRole.COLLECTOR && user.getStatus() == UserStatus.ACTIVE)
				.map((user) -> {
					user.setName(name);
					user.setUpdatedAt(updatedAt);
					return user;
				});
		}
	}

	private static final class InMemorySessionStore implements SessionStore {
		private final List<SessionRecord> values = new ArrayList<>();

		@Override
		public SessionRecord save(SessionRecord session) {
			if (session.getId() == null) {
				session.setId(UUID.randomUUID().toString());
			}
			values.removeIf((existing) -> existing.getId().equals(session.getId()));
			values.add(session);
			return session;
		}

		@Override
		public Optional<SessionRecord> findById(String id) {
			return values.stream().filter((session) -> session.getId().equals(id)).findFirst();
		}

		@Override
		public Optional<SessionRecord> findByTokenHash(String tokenHash) {
			return values.stream().filter((session) -> tokenHash.equals(session.getTokenHash())).findFirst();
		}

		@Override
		public Optional<SessionRecord> findActiveWebByUserId(String userId) {
			return values.stream().filter((session) ->
				userId.equals(session.getUserId())
					&& session.getType() == SessionType.WEB
					&& session.getStatus() == SessionStatus.ACTIVE
			).findFirst();
		}

		@Override
		public List<SessionRecord> findActiveByUserId(String userId) {
			return values.stream().filter((session) ->
				userId.equals(session.getUserId()) && session.getStatus() == SessionStatus.ACTIVE
			).toList();
		}

		@Override
		public boolean transitionStatus(String sessionId, SessionStatus expected, SessionStatus target) {
			Optional<SessionRecord> session = findById(sessionId);
			if (session.isEmpty() || session.get().getStatus() != expected) {
				return false;
			}
			session.get().setStatus(target);
			return true;
		}

		@Override
		public long transitionAllActiveByUserId(String userId, SessionStatus target) {
			long changed = 0;
			for (SessionRecord session : values) {
				if (userId.equals(session.getUserId()) && session.getStatus() == SessionStatus.ACTIVE) {
					session.setStatus(target);
					changed++;
				}
			}
			return changed;
		}

		@Override
		public boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt) {
			Optional<SessionRecord> session = findById(sessionId);
			if (session.isEmpty() || session.get().getStatus() != SessionStatus.ACTIVE) {
				return false;
			}
			session.get().setLastAccessAt(lastAccessAt);
			session.get().setExpiresAt(expiresAt);
			return true;
		}
	}
}

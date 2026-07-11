package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.SessionStore;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionConcurrencyTests {

	@Test
	void concurrentLogoutCannotBeOverwrittenByAStaleAuthenticationTouch() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
		OpaqueTokenService tokens = new OpaqueTokenService();
		OpaqueTokenService.TokenPair token = tokens.issue();
		SessionRecord persisted = new SessionRecord();
		persisted.setId("session-1");
		persisted.setUserId("admin-1");
		persisted.setTokenHash(token.hash());
		persisted.setType(SessionType.WEB);
		persisted.setStatus(SessionStatus.ACTIVE);
		persisted.setCreatedAt(Instant.now(clock));
		persisted.setLastAccessAt(Instant.now(clock));
		persisted.setExpiresAt(Instant.now(clock).plus(Duration.ofHours(12)));
		LogoutDuringTouchStore sessions = new LogoutDuringTouchStore(persisted);
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		UserAccount admin = new UserAccount();
		admin.setId("admin-1");
		admin.setUsername("admin");
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		when(users.findById("admin-1")).thenReturn(Optional.of(admin));
		SessionService service = new SessionService(
			sessions,
			users,
			tokens,
			clock,
			Duration.ofHours(12),
			Duration.ofDays(30)
		);

		assertThatThrownBy(() -> service.authenticateWeb(token.raw()))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("SESSION_INVALID")
			);
		assertThat(sessions.persisted.getStatus()).isEqualTo(SessionStatus.REVOKED);
	}

	private static final class LogoutDuringTouchStore implements SessionStore {
		private SessionRecord persisted;
		private boolean simulateLogout = true;

		private LogoutDuringTouchStore(SessionRecord persisted) {
			this.persisted = persisted;
		}

		@Override
		public SessionRecord save(SessionRecord session) {
			if (simulateLogout) {
				simulateLogout = false;
				persisted.setStatus(SessionStatus.REVOKED);
			}
			persisted = session;
			return session;
		}

		@Override
		public Optional<SessionRecord> findById(String id) {
			return Optional.of(copy(persisted));
		}

		@Override
		public Optional<SessionRecord> findByTokenHash(String tokenHash) {
			return Optional.of(copy(persisted));
		}

		@Override
		public Optional<SessionRecord> findActiveWebByUserId(String userId) {
			return Optional.empty();
		}

		@Override
		public List<SessionRecord> findActiveByUserId(String userId) {
			return List.of();
		}

		@Override
		public boolean transitionStatus(String sessionId, SessionStatus expected, SessionStatus target) {
			if (!sessionId.equals(persisted.getId()) || persisted.getStatus() != expected) {
				return false;
			}
			persisted.setStatus(target);
			return true;
		}

		@Override
		public long transitionAllActiveByUserId(String userId, SessionStatus target) {
			if (userId.equals(persisted.getUserId()) && persisted.getStatus() == SessionStatus.ACTIVE) {
				persisted.setStatus(target);
				return 1;
			}
			return 0;
		}

		@Override
		public boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt) {
			if (simulateLogout) {
				simulateLogout = false;
				persisted.setStatus(SessionStatus.REVOKED);
			}
			return false;
		}

		private SessionRecord copy(SessionRecord source) {
			SessionRecord copy = new SessionRecord();
			copy.setId(source.getId());
			copy.setUserId(source.getUserId());
			copy.setTokenHash(source.getTokenHash());
			copy.setType(source.getType());
			copy.setStatus(source.getStatus());
			copy.setCreatedAt(source.getCreatedAt());
			copy.setLastAccessAt(source.getLastAccessAt());
			copy.setExpiresAt(source.getExpiresAt());
			return copy;
		}
	}
}

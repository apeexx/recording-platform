package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.SessionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitSessionServiceTests {
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneOffset.UTC);
	private final OpaqueTokenService tokens = new OpaqueTokenService();
	private InMemorySessions sessions;
	private IdentityDirectory identities;
	private SessionService service;

	@BeforeEach
	void setUp() {
		sessions = new InMemorySessions();
		identities = mock(IdentityDirectory.class);
		when(identities.findById("WEB-0123456789abcdef01234567")).thenReturn(Optional.of(
			new IdentityUser("WEB-0123456789abcdef01234567", UserType.WEB, "admin", "管理员",
				UserRole.ADMIN, UserStatus.ACTIVE, false, null, null)
		));
		when(identities.findById("MINI-0123456789abcdef01234567")).thenReturn(Optional.of(
			new IdentityUser("MINI-0123456789abcdef01234567", UserType.MINIPROGRAM, "682913", "录音员",
				UserRole.COLLECTOR, UserStatus.ACTIVE, false, null, null)
		));
		service = new SessionService(sessions, identities, tokens, clock, Duration.ofHours(12), Duration.ofDays(30));
	}

	@Test
	void webAndMiniProgramSessionsAreIndependentAndEachTypeAllowsOnlyOneActiveSession() {
		var web = service.issueWeb("WEB-0123456789abcdef01234567");
		var mini = service.issueMiniProgram("MINI-0123456789abcdef01234567");

		assertThat(service.active("WEB-0123456789abcdef01234567", SessionType.WEB)).isPresent();
		assertThat(service.active("MINI-0123456789abcdef01234567", SessionType.MINIPROGRAM)).isPresent();
		assertThat(service.authenticateWeb(web.token()).role()).isEqualTo(UserRole.ADMIN);
		assertThat(service.authenticateMiniProgram(mini.token()).role()).isEqualTo(UserRole.COLLECTOR);
	}

	@Test
	void bothTakeoverTypesReplaceOnlyTheirBoundSessionAndAreOneTime() {
		var web = service.issueWeb("WEB-0123456789abcdef01234567");
		var webTakeover = service.issueWebTakeover("WEB-0123456789abcdef01234567", web.session().getId());
		var nextWeb = service.confirmWebTakeover(webTakeover.token());
		assertReplaced(() -> service.authenticateWeb(web.token()));
		assertThat(service.authenticateWeb(nextWeb.token()).userId()).startsWith("WEB-");
		assertInvalidTakeover(() -> service.confirmWebTakeover(webTakeover.token()));

		var mini = service.issueMiniProgram("MINI-0123456789abcdef01234567");
		var miniTakeover = service.issueMiniProgramTakeover("MINI-0123456789abcdef01234567", mini.session().getId());
		var nextMini = service.confirmMiniProgramTakeover(miniTakeover.token());
		assertReplaced(() -> service.authenticateMiniProgram(mini.token()));
		assertThat(service.authenticateMiniProgram(nextMini.token()).userId()).startsWith("MINI-");
		assertInvalidTakeover(() -> service.confirmMiniProgramTakeover(miniTakeover.token()));
	}

	@Test
	void expiredAndWrongTypeTakeoversAndSessionPrefixMismatchesAreRejected() {
		var web = service.issueWeb("WEB-0123456789abcdef01234567");
		var takeover = service.issueWebTakeover("WEB-0123456789abcdef01234567", web.session().getId());
		assertInvalidTakeover(() -> service.confirmMiniProgramTakeover(takeover.token()));

		SessionRecord wrongPrefix = sessions.record(tokens.issue(), "MINI-0123456789abcdef01234567", SessionType.WEB,
			Instant.now(clock).plusSeconds(60));
		assertThatThrownBy(() -> service.authenticateWeb(sessions.rawFor(wrongPrefix)))
			.isInstanceOfSatisfying(ApiException.class, exception ->
				assertThat(exception.getCode()).isEqualTo("SESSION_INVALID"));

		SessionRecord expiredTakeover = sessions.record(tokens.issue(), "MINI-0123456789abcdef01234567",
			SessionType.MINIPROGRAM_TAKEOVER, Instant.now(clock).minusSeconds(1));
		expiredTakeover.setReplacedSessionId("missing");
		assertInvalidTakeover(() -> service.confirmMiniProgramTakeover(sessions.rawFor(expiredTakeover)));
	}

	@Test
	void concurrentConsumptionOfOneTakeoverTokenHasExactlyOneWinner() throws Exception {
		var active = service.issueMiniProgram("MINI-0123456789abcdef01234567");
		var takeover = service.issueMiniProgramTakeover("MINI-0123456789abcdef01234567", active.session().getId());
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.Callable<String> consume = () -> {
				start.await();
				try {
					service.confirmMiniProgramTakeover(takeover.token());
					return "SUCCESS";
				} catch (ApiException exception) {
					return exception.getCode();
				}
			};
			var first = executor.submit(consume);
			var second = executor.submit(consume);
			start.countDown();
			assertThat(List.of(first.get(), second.get()))
				.containsExactlyInAnyOrder("SUCCESS", "TAKEOVER_TOKEN_INVALID");
		} finally {
			executor.shutdownNow();
		}
	}

	private void assertReplaced(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class,
			exception -> assertThat(exception.getCode()).isEqualTo("SESSION_REPLACED"));
	}

	private void assertInvalidTakeover(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class,
			exception -> assertThat(exception.getCode()).isEqualTo("TAKEOVER_TOKEN_INVALID"));
	}

	private final class InMemorySessions implements SessionStore {
		private final List<SessionRecord> values = new ArrayList<>();
		private final java.util.Map<String, String> rawById = new java.util.HashMap<>();

		@Override public SessionRecord save(SessionRecord session) {
			if (session.getId() == null) session.setId(UUID.randomUUID().toString());
			values.removeIf(value -> value.getId().equals(session.getId())); values.add(session); return session;
		}
		SessionRecord record(OpaqueTokenService.TokenPair pair, String userId, SessionType type, Instant expiresAt) {
			SessionRecord record = new SessionRecord(); record.setId(UUID.randomUUID().toString()); record.setUserId(userId);
			record.setTokenHash(pair.hash()); record.setType(type); record.setStatus(SessionStatus.ACTIVE);
			record.setCreatedAt(Instant.now(clock)); record.setLastAccessAt(Instant.now(clock)); record.setExpiresAt(expiresAt);
			rawById.put(record.getId(), pair.raw()); return save(record);
		}
		String rawFor(SessionRecord record) { return rawById.get(record.getId()); }
		@Override public Optional<SessionRecord> findById(String id) { return values.stream().filter(v -> v.getId().equals(id)).findFirst(); }
		@Override public Optional<SessionRecord> findByTokenHash(String hash) { return values.stream().filter(v -> hash.equals(v.getTokenHash())).findFirst(); }
		@Override public Optional<SessionRecord> findActiveByUserIdAndType(String userId, SessionType type) {
			return values.stream().filter(v -> userId.equals(v.getUserId()) && v.getType() == type && v.getStatus() == SessionStatus.ACTIVE).findFirst();
		}
		@Override public List<SessionRecord> findActiveByUserId(String userId) { return values.stream().filter(v -> userId.equals(v.getUserId()) && v.getStatus() == SessionStatus.ACTIVE).toList(); }
		@Override public synchronized boolean transitionStatus(String id, SessionStatus expected, SessionStatus target) {
			var value = findById(id); if (value.isEmpty() || value.get().getStatus() != expected) return false;
			value.get().setStatus(target); return true;
		}
		@Override public long transitionAllActiveByUserId(String userId, SessionStatus target) {
			long count = 0; for (var value : values) if (userId.equals(value.getUserId()) && value.getStatus() == SessionStatus.ACTIVE) { value.setStatus(target); count++; }
			return count;
		}
		@Override public boolean touchActive(String id, Instant access, Instant expiry) {
			var value = findById(id); if (value.isEmpty() || value.get().getStatus() != SessionStatus.ACTIVE) return false;
			value.get().setLastAccessAt(access); value.get().setExpiresAt(expiry); return true;
		}
	}
}

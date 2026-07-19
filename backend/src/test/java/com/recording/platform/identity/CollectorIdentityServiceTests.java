package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.CollectorIdentityService;
import com.recording.platform.identity.service.MiniProgramLoginResult;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.SessionStore;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CollectorIdentityServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC);
	private UserStore users;
	private SessionStore sessionStore;
	private BCryptPasswordEncoder encoder;
	private SessionService sessions;
	private CollectorIdentityService service;

	@BeforeEach
	void setUp() {
		users = org.mockito.Mockito.mock(UserStore.class);
		sessionStore = org.mockito.Mockito.mock(SessionStore.class);
		encoder = new BCryptPasswordEncoder();
		sessions = new SessionService(
			sessionStore, users, new OpaqueTokenService(), CLOCK, Duration.ofHours(12), Duration.ofDays(30)
		);
		service = new CollectorIdentityService(users, sessions, encoder, CLOCK);
	}

	@Test
	void completesProfileAtomicallyAndPasswordLoginReturnsTheSameCollector() {
		UserAccount collector = collector("collector-1");
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		when(users.findByUsername("682913")).thenReturn(Optional.empty(), Optional.of(collector));
		when(users.completeCollectorProfileIfActive(eq("collector-1"), eq("682913"), eq("张三"), any(), any()))
			.thenAnswer((invocation) -> {
				collector.setUsername(invocation.getArgument(1));
				collector.setName(invocation.getArgument(2));
				collector.setPasswordHash(invocation.getArgument(3));
				return Optional.of(collector);
			});
		when(sessionStore.save(any())).thenAnswer((invocation) -> {
			SessionRecord record = invocation.getArgument(0);
			record.setId("mini-session-1");
			return record;
		});

		UserAccount completed = service.completeProfile("collector-1", " 张三 ", "682913", "Password-1");
		MiniProgramLoginResult login = service.login("682913", "Password-1");

		assertThat(completed.getName()).isEqualTo("张三");
		assertThat(encoder.matches("Password-1", completed.getPasswordHash())).isTrue();
		assertThat(login.user().getId()).isEqualTo("collector-1");
		assertThat(login.token()).isNotBlank();
	}

	@Test
	void rejectsInvalidOrDuplicateCollectorAccounts() {
		UserAccount collector = collector("collector-1");
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		when(users.findByUsername("682913")).thenReturn(Optional.of(collector("collector-2")));

		assertApiError(() -> service.completeProfile("collector-1", "张三", "012345", "Password-1"),
			422, "INVALID_COLLECTOR_ACCOUNT");
		assertApiError(() -> service.completeProfile("collector-1", "张三", "682913", "Password-1"),
			409, "USERNAME_EXISTS");
	}

	@Test
	void duplicateIndexRaceUsesTheSameUsernameConflictContract() {
		UserAccount collector = collector("collector-1");
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		when(users.findByUsername("682913")).thenReturn(Optional.empty());
		when(users.completeCollectorProfileIfActive(eq("collector-1"), eq("682913"), eq("张三"), any(), any()))
			.thenThrow(new DuplicateKeyException("simulated unique race"));

		assertApiError(() -> service.completeProfile("collector-1", "张三", "682913", "Password-1"),
			409, "USERNAME_EXISTS");
	}

	@Test
	void changingPasswordRequiresTheCurrentPasswordAndRevokesExistingSessions() {
		UserAccount collector = collector("collector-1");
		collector.setPasswordHash(encoder.encode("Password-1"));
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		when(users.updateCollectorPasswordIfActive(eq("collector-1"), eq(collector.getPasswordHash()), any(), any()))
			.thenReturn(Optional.of(collector));

		assertApiError(() -> service.changePassword("collector-1", "wrong-password", "Password-2"),
			401, "INVALID_CREDENTIALS");
		service.changePassword("collector-1", "Password-1", "Password-2");

		verify(sessionStore).transitionAllActiveByUserId("collector-1", SessionStatus.REVOKED);
	}

	@Test
	void accountLoginNeverAuthenticatesBackendOrDisabledUsers() {
		UserAccount backend = collector("admin-1");
		backend.setRole(UserRole.ADMIN);
		backend.setPasswordHash(encoder.encode("Password-1"));
		when(users.findByUsername("682913")).thenReturn(Optional.of(backend));

		assertApiError(() -> service.login("682913", "Password-1"), 401, "INVALID_CREDENTIALS");
	}

	private UserAccount collector(String id) {
		UserAccount user = new UserAccount();
		user.setId(id);
		user.setInternalUserNo("USR-000001");
		user.setRole(UserRole.COLLECTOR);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, int status, String code) {
		assertThatThrownBy(callable).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(status);
			assertThat(exception.getCode()).isEqualTo(code);
		});
	}
}

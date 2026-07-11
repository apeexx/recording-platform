package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DuplicateKeyException;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class InitialAdminInitializerTests {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void missingEnvironmentValuesDoNotTouchTheDatabaseOrCreateAnAdmin() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		InitialAdminInitializer initializer = new InitialAdminInitializer(
			users,
			new BCryptPasswordEncoder(),
			CLOCK,
			"",
			""
		);

		initializer.run();

		verify(users, never()).existsByRole(any());
		verify(users, never()).save(any());
	}

	@Test
	void createsFirstAdminWithBcryptAndMandatoryFirstPasswordChange() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		when(users.existsByRole(UserRole.ADMIN)).thenReturn(false);
		when(users.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		InitialAdminInitializer initializer = new InitialAdminInitializer(
			users,
			encoder,
			CLOCK,
			"root-admin",
			"SafePassword-2026"
		);

		initializer.run();

		ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
		verify(users).save(captor.capture());
		UserAccount created = captor.getValue();
		assertThat(created.getId()).isEqualTo("initial-admin");
		assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(created.isFirstPasswordChangeRequired()).isTrue();
		assertThat(created.getInternalUserNo()).startsWith("USR-");
		assertThat(encoder.matches("SafePassword-2026", created.getPasswordHash())).isTrue();
		assertThat(created.getCreatedAt()).isEqualTo(Instant.parse("2026-07-11T12:00:00Z"));
	}

	@Test
	void invalidBootstrapPasswordFailsClosedWithASanitizedMessage() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		when(users.existsByRole(UserRole.ADMIN)).thenReturn(false);
		InitialAdminInitializer initializer = new InitialAdminInitializer(
			users,
			new BCryptPasswordEncoder(),
			CLOCK,
			"root-admin",
			"x".repeat(73)
		);

		assertThatThrownBy(initializer::run)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("首管理员初始化密码不符合安全要求")
			.hasMessageNotContaining("x".repeat(73));
		verify(users, never()).save(any());
	}

	@Test
	void doesNotCreateAnotherAdminWhenOneAlreadyExists() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		when(users.existsByRole(UserRole.ADMIN)).thenReturn(true);
		InitialAdminInitializer initializer = new InitialAdminInitializer(
			users,
			new BCryptPasswordEncoder(),
			CLOCK,
			"root-admin",
			"SafePassword-2026"
		);

		initializer.run();

		verify(users, never()).save(any());
	}

	@Test
	void duplicateUsernameWithoutAnAdminFailsClosedWithoutLeakingTheBootstrapPassword() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		when(users.existsByRole(UserRole.ADMIN)).thenReturn(false, false);
		when(users.save(any())).thenThrow(new DuplicateKeyException("duplicate username"));
		InitialAdminInitializer initializer = new InitialAdminInitializer(
			users,
			new BCryptPasswordEncoder(),
			CLOCK,
			"root-admin",
			"SafePassword-2026"
		);

		assertThatThrownBy(initializer::run)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("首管理员初始化失败：用户名或内部编号冲突")
			.hasMessageNotContaining("SafePassword-2026");
	}
}

package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserConcurrencyTests {

	@Test
	void concurrentDisableCannotBeOverwrittenByAStalePasswordChange() {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		UserAccount persisted = new UserAccount();
		persisted.setId("admin-1");
		persisted.setUsername("admin");
		persisted.setRole(UserRole.ADMIN);
		persisted.setStatus(UserStatus.ACTIVE);
		persisted.setPasswordHash(encoder.encode("InitialPassword-1"));
		persisted.setFirstPasswordChangeRequired(true);
		DisableDuringPasswordUpdateStore users = new DisableDuringPasswordUpdateStore(persisted);
		SessionService sessions = org.mockito.Mockito.mock(SessionService.class);
		WebAuthenticationService authentication = new WebAuthenticationService(
			users,
			sessions,
			encoder,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);

		assertThatThrownBy(() -> authentication.changePassword(
			"admin-1",
			"InitialPassword-1",
			"ChangedPassword-2"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isIn("ACCOUNT_DISABLED", "ACCOUNT_STATE_CHANGED")
		);
		assertThat(users.persisted.getStatus()).isEqualTo(UserStatus.DISABLED);
	}

	private static final class DisableDuringPasswordUpdateStore implements UserStore {
		private UserAccount persisted;

		private DisableDuringPasswordUpdateStore(UserAccount persisted) {
			this.persisted = persisted;
		}

		@Override
		public UserAccount save(UserAccount user) {
			persisted.setStatus(UserStatus.DISABLED);
			persisted = user;
			return user;
		}

		@Override
		public Optional<UserAccount> findById(String id) {
			return Optional.of(copy(persisted));
		}

		@Override
		public Optional<UserAccount> findByUsername(String username) {
			return Optional.empty();
		}

		@Override
		public Optional<UserAccount> findByWechatIdentity(String appId, String openId) {
			return Optional.empty();
		}

		@Override
		public boolean existsByRole(UserRole role) {
			return persisted.getRole() == role;
		}

		@Override
		public Page<UserAccount> findAll(Pageable pageable) {
			return new PageImpl<>(java.util.List.of(copy(persisted)));
		}

		@Override
		public Page<UserAccount> findAllBackend(Pageable pageable) {
			return findAll(pageable);
		}

		@Override
		public Optional<UserAccount> disableBackendIfActive(String userId, Instant updatedAt) {
			persisted.setStatus(UserStatus.DISABLED);
			return Optional.of(copy(persisted));
		}

		@Override
		public boolean updatePasswordIfActive(
			String userId,
			String expectedPasswordHash,
			String passwordHash,
			Instant updatedAt
		) {
			persisted.setStatus(UserStatus.DISABLED);
			return false;
		}

		@Override
		public Optional<UserAccount> updateCollectorNameIfActive(String userId, String name, Instant updatedAt) {
			persisted.setStatus(UserStatus.DISABLED);
			return Optional.empty();
		}

		private UserAccount copy(UserAccount source) {
			UserAccount copy = new UserAccount();
			copy.setId(source.getId());
			copy.setUsername(source.getUsername());
			copy.setRole(source.getRole());
			copy.setStatus(source.getStatus());
			copy.setPasswordHash(source.getPasswordHash());
			copy.setFirstPasswordChangeRequired(source.isFirstPasswordChangeRequired());
			return copy;
		}
	}
}

package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.AdminUserService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminUserServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void createsReviewerWithBcryptAndRejectsCollectorAsABackgroundAccount() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		SessionService sessions = org.mockito.Mockito.mock(SessionService.class);
		when(users.findByUsername("reviewer-1")).thenReturn(Optional.empty());
		when(users.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		AdminUserService service = new AdminUserService(users, sessions, encoder, CLOCK);

		service.create(new CreateBackendUserRequest(
			"reviewer-1",
			"一审员",
			UserRole.REVIEWER,
			"ReviewerPassword-1"
		));

		ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
		verify(users).save(captor.capture());
		assertThat(captor.getValue().getRole()).isEqualTo(UserRole.REVIEWER);
		assertThat(captor.getValue().isFirstPasswordChangeRequired()).isTrue();
		assertThat(encoder.matches("ReviewerPassword-1", captor.getValue().getPasswordHash())).isTrue();
		assertThatThrownBy(() -> service.create(new CreateBackendUserRequest(
			"collector-1",
			"录音员",
			UserRole.COLLECTOR,
			"CollectorPassword-1"
		))).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("INVALID_BACKEND_ROLE");
		});
	}

	@Test
	void initialPasswordBeyondTheBcryptLimitUsesTheWeakPasswordContract() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		AdminUserService service = new AdminUserService(
			users,
			org.mockito.Mockito.mock(SessionService.class),
			new BCryptPasswordEncoder(),
			CLOCK
		);

		assertThatThrownBy(() -> service.create(new CreateBackendUserRequest(
			"reviewer-1",
			"一审员",
			UserRole.REVIEWER,
			"x".repeat(73)
		))).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("PASSWORD_TOO_WEAK");
		});
	}

	@Test
	void disablingAnAccountRevokesItsSessions() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		SessionService sessions = org.mockito.Mockito.mock(SessionService.class);
		UserAccount reviewer = new UserAccount();
		reviewer.setId("reviewer-1");
		reviewer.setStatus(UserStatus.ACTIVE);
		when(users.findById("reviewer-1")).thenReturn(Optional.of(reviewer));
		when(users.disableBackendIfActive(org.mockito.ArgumentMatchers.eq("reviewer-1"), any()))
			.thenAnswer((invocation) -> {
				reviewer.setStatus(UserStatus.DISABLED);
				return Optional.of(reviewer);
			});
		AdminUserService service = new AdminUserService(users, sessions, new BCryptPasswordEncoder(), CLOCK);

		service.disable("reviewer-1");

		assertThat(reviewer.getStatus()).isEqualTo(UserStatus.DISABLED);
		verify(sessions).revokeAll("reviewer-1");
	}

	@Test
	void backendAccountListAndDisableDoNotCrossIntoCollectorAccounts() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		SessionService sessions = org.mockito.Mockito.mock(SessionService.class);
		UserAccount reviewer = new UserAccount();
		reviewer.setId("reviewer-1");
		reviewer.setRole(UserRole.REVIEWER);
		reviewer.setStatus(UserStatus.ACTIVE);
		UserAccount collector = new UserAccount();
		collector.setId("collector-1");
		collector.setRole(UserRole.COLLECTOR);
		collector.setStatus(UserStatus.ACTIVE);
		when(users.findAllBackend(PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(reviewer)));
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		AdminUserService service = new AdminUserService(users, sessions, new BCryptPasswordEncoder(), CLOCK);

		assertThat(service.list(0, 20).getContent())
			.extracting((user) -> user.role())
			.containsExactly(UserRole.REVIEWER);
		assertThatThrownBy(() -> service.disable("collector-1"))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("INVALID_BACKEND_ROLE")
			);
	}

	@Test
	void searchesCollectorsAndResetsBackendPasswordWithSessionRevocation() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		SessionService sessions = org.mockito.Mockito.mock(SessionService.class);
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		UserAccount collector = new UserAccount();
		collector.setId("collector-1");
		collector.setRole(UserRole.COLLECTOR);
		collector.setStatus(UserStatus.ACTIVE);
		UserAccount reviewer = new UserAccount();
		reviewer.setId("reviewer-1");
		reviewer.setRole(UserRole.REVIEWER);
		reviewer.setStatus(UserStatus.ACTIVE);
		when(users.search("张", UserRole.COLLECTOR, PageRequest.of(0, 20)))
			.thenReturn(new PageImpl<>(List.of(collector)));
		when(users.findById("reviewer-1")).thenReturn(Optional.of(reviewer));
		when(users.resetBackendPasswordIfActive(org.mockito.ArgumentMatchers.eq("reviewer-1"), any(), any()))
			.thenReturn(Optional.of(reviewer));
		when(users.findById("collector-1")).thenReturn(Optional.of(collector));
		when(users.resetCollectorPasswordIfActive(org.mockito.ArgumentMatchers.eq("collector-1"), any(), any()))
			.thenReturn(Optional.of(collector));
		AdminUserService service = new AdminUserService(users, sessions, encoder, CLOCK);

		assertThat(service.search(" 张 ", UserRole.COLLECTOR, 0, 20).getContent()).hasSize(1);
		service.resetPassword("reviewer-1", "NewPassword-1");
		service.resetPassword("collector-1", "CollectorPassword-1");

		verify(users).resetBackendPasswordIfActive(
			org.mockito.ArgumentMatchers.eq("reviewer-1"),
			org.mockito.ArgumentMatchers.argThat((String hash) -> encoder.matches("NewPassword-1", hash)),
			org.mockito.ArgumentMatchers.eq(Instant.parse("2026-07-11T12:00:00Z"))
		);
		verify(sessions).revokeAll("reviewer-1");
		verify(users).resetCollectorPasswordIfActive(
			org.mockito.ArgumentMatchers.eq("collector-1"),
			org.mockito.ArgumentMatchers.argThat((String hash) -> encoder.matches("CollectorPassword-1", hash)),
			org.mockito.ArgumentMatchers.eq(Instant.parse("2026-07-11T12:00:00Z"))
		);
		verify(sessions).revokeAll("collector-1");
	}
}

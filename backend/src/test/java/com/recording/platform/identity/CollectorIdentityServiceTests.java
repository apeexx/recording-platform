package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.service.CollectorIdentityService;
import com.recording.platform.identity.service.OpaqueTokenService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.MiniProgramUserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

class CollectorIdentityServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneOffset.UTC);
	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

	@Test
	void completesProfileWithAccountFieldAndBcryptPassword() {
		MiniProgramUserStore users = mock(MiniProgramUserStore.class); MiniProgramUser user = mini();
		when(users.findById(user.getId())).thenReturn(Optional.of(user)); when(users.findByAccount("682913")).thenReturn(Optional.empty());
		when(users.completeProfileIfActive(eq(user.getId()), eq("682913"), eq("张三"), any(), eq(Instant.now(CLOCK))))
			.thenAnswer(invocation -> { user.setAccount("682913"); user.setName("张三"); user.setPasswordHash(invocation.getArgument(3)); return Optional.of(user); });
		CollectorIdentityService service = new CollectorIdentityService(users, mock(SessionService.class), passwords, CLOCK);

		MiniProgramUser completed = service.completeProfile(user.getId(), " 张三 ", "682913", "Password-1");
		assertThat(completed.getAccount()).isEqualTo("682913"); assertThat(passwords.matches("Password-1", completed.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsDuplicateOrMalformedMiniProgramAccounts() {
		MiniProgramUserStore users = mock(MiniProgramUserStore.class); MiniProgramUser user = mini(); MiniProgramUser other = mini(); other.setId("MINI-1123456789abcdef01234567");
		when(users.findById(user.getId())).thenReturn(Optional.of(user)); when(users.findByAccount("682913")).thenReturn(Optional.of(other));
		CollectorIdentityService service = new CollectorIdentityService(users, mock(SessionService.class), passwords, CLOCK);
		assertThatThrownBy(() -> service.completeProfile(user.getId(), "张三", "682913", "Password-1"))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("USERNAME_EXISTS"));
		assertThatThrownBy(() -> service.completeProfile(user.getId(), "张三", "012345", "Password-1"))
			.isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("INVALID_COLLECTOR_ACCOUNT"));
	}

	@Test void passwordChangeUsesCasAndRevokesAllMiniProgramSessions(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);SessionService sessions=mock(SessionService.class);MiniProgramUser user=mini();user.setAccount("682913");user.setPasswordHash(passwords.encode("Password-1"));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));when(users.updatePasswordIfActive(eq(user.getId()),eq(user.getPasswordHash()),any(),eq(Instant.now(CLOCK)))).thenReturn(Optional.of(user));
		new CollectorIdentityService(users,sessions,passwords,CLOCK).changePassword(user.getId(),"Password-1","Password-2");verify(sessions).revokeAll(user.getId());}

	@Test void passwordChangeRevokesAnIssuedMiniProgramSession(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);MiniProgramUser user=mini();user.setAccount("682913");user.setName("张三");user.setPasswordHash(passwords.encode("Password-1"));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));when(users.updatePasswordIfActive(eq(user.getId()),eq(user.getPasswordHash()),any(),eq(Instant.now(CLOCK)))).thenAnswer(invocation->{user.setPasswordHash(invocation.getArgument(2));return Optional.of(user);});
		IdentityDirectory identities=mock(IdentityDirectory.class);when(identities.findById(user.getId())).thenAnswer(invocation->Optional.of(new IdentityUser(user.getId(),UserType.MINIPROGRAM,user.getAccount(),user.getName(),UserRole.COLLECTOR,user.getStatus(),false,user.getCreatedAt(),user.getUpdatedAt())));
		SessionService sessions=new SessionService(new InMemorySessionStore(),identities,new OpaqueTokenService(),CLOCK,Duration.ofHours(12),Duration.ofDays(30));var issued=sessions.issueMiniProgram(user.getId());
		new CollectorIdentityService(users,sessions,passwords,CLOCK).changePassword(user.getId(),"Password-1","Password-2");
		assertThatThrownBy(()->sessions.authenticateMiniProgram(issued.token())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("SESSION_INVALID"));}

	@Test void completedProfileCannotBeOverwritten(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);MiniProgramUser user=mini();user.setAccount("682913");user.setPasswordHash("hash");when(users.findById(user.getId())).thenReturn(Optional.of(user));
		assertThatThrownBy(()->new CollectorIdentityService(users,mock(SessionService.class),passwords,CLOCK).completeProfile(user.getId(),"张三","682914","Password-1"))
			.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("PROFILE_ALREADY_COMPLETED"));}

	@Test void concurrentMiniProgramAccountClaimMapsDuplicateKeyToUsernameExists(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);MiniProgramUser user=mini();when(users.findById(user.getId())).thenReturn(Optional.of(user));when(users.findByAccount("682913")).thenReturn(Optional.empty());
		when(users.completeProfileIfActive(eq(user.getId()),eq("682913"),eq("张三"),any(),eq(Instant.now(CLOCK)))).thenThrow(new DuplicateKeyException("account"));
		assertThatThrownBy(()->new CollectorIdentityService(users,mock(SessionService.class),passwords,CLOCK).completeProfile(user.getId(),"张三","682913","Password-1"))
			.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("USERNAME_EXISTS"));}

	@Test void nameUpdatePreservesCollectorBoundary(){MiniProgramUserStore users=mock(MiniProgramUserStore.class);MiniProgramUser user=mini();when(users.updateNameIfActive(eq(user.getId()),eq("张三"),eq(Instant.now(CLOCK)))).thenAnswer(i->{user.setName("张三");return Optional.of(user);});
		assertThat(new CollectorIdentityService(users,mock(SessionService.class),passwords,CLOCK).setName(user.getId()," 张三 ").getName()).isEqualTo("张三");}

	private MiniProgramUser mini() { MiniProgramUser user=new MiniProgramUser();user.setId("MINI-0123456789abcdef01234567");user.setStatus(UserStatus.ACTIVE);return user; }
}

package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.service.AdminUserService;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminUserServiceTests {
	private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"),ZoneOffset.UTC);
	private final WebUserStore web=mock(WebUserStore.class); private final MiniProgramUserStore mini=mock(MiniProgramUserStore.class);
	private final IdentityDirectory directory=mock(IdentityDirectory.class); private final SessionService sessions=mock(SessionService.class);
	private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();
	private AdminUserService service(){return new AdminUserService(web,mini,directory,sessions,passwords,CLOCK);}

	@Test void createsOnlyWebRolesWithPrefixedIds(){when(web.findByUsername("reviewer")).thenReturn(Optional.empty());when(web.save(any())).thenAnswer(i->i.getArgument(0));
		var response=service().create(new CreateBackendUserRequest("reviewer","审核员",UserRole.REVIEWER,"Password-1"));
		assertThat(response.id()).matches("WEB-[0-9a-f]{24}");assertThat(response.userType()).isEqualTo(UserType.WEB);assertThat(response.loginName()).isEqualTo("reviewer");
		assertThatThrownBy(()->service().create(new CreateBackendUserRequest("collector","采集员",UserRole.COLLECTOR,"Password-1")))
			.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("INVALID_BACKEND_ROLE"));}

	@Test void rejectsWebPasswordsOutsideBcryptBoundary(){assertThatThrownBy(()->service().create(new CreateBackendUserRequest("reviewer","审核员",UserRole.REVIEWER,"x".repeat(73))))
		.isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("PASSWORD_TOO_WEAK"));}

	@Test void collectorSearchAndUpdatesUseOnlyMiniProgramStore(){MiniProgramUser user=new MiniProgramUser();user.setId("MINI-0123456789abcdef01234567");user.setAccount("682913");user.setStatus(UserStatus.ACTIVE);
		when(mini.search("张",org.springframework.data.domain.PageRequest.of(0,20))).thenReturn(new PageImpl<>(java.util.List.of(user)));
		when(mini.findById(user.getId())).thenReturn(Optional.of(user));when(mini.findByAccount("682914")).thenReturn(Optional.empty());when(mini.updateAccountIfActive(org.mockito.ArgumentMatchers.eq(user.getId()),org.mockito.ArgumentMatchers.eq("682914"),any())).thenAnswer(i->{user.setAccount("682914");return Optional.of(user);});
		assertThat(service().search(" 张 ",UserRole.COLLECTOR,0,20).getContent()).extracting(r->r.userType()).containsExactly(UserType.MINIPROGRAM);
		assertThat(service().updateCollectorAccount(user.getId(),"682914").loginName()).isEqualTo("682914");verify(sessions).revokeAll(user.getId());}

	@Test
	void unfilteredSearchMergesBothStoresBeforeGlobalSortAndPagination() {
		WebUser newest = webUser("WEB-000000000000000000000001", "admin", Instant.parse("2026-07-20T04:00:00Z"));
		MiniProgramUser next = miniUser("MINI-000000000000000000000001", "682913", Instant.parse("2026-07-20T03:00:00Z"));
		WebUser middle = webUser("WEB-000000000000000000000002", "reviewer", Instant.parse("2026-07-20T02:00:00Z"));
		MiniProgramUser older = miniUser("MINI-000000000000000000000002", "682914", Instant.parse("2026-07-20T01:00:00Z"));
		WebUser oldest = webUser("WEB-000000000000000000000003", "auditor", Instant.parse("2026-07-20T00:00:00Z"));
		when(web.search(org.mockito.ArgumentMatchers.eq("用户"), org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
			.thenAnswer(invocation -> new PageImpl<>(java.util.List.of(newest, middle, oldest), invocation.getArgument(2), 3));
		when(mini.search(org.mockito.ArgumentMatchers.eq("用户"), any(Pageable.class)))
			.thenAnswer(invocation -> new PageImpl<>(java.util.List.of(next, older), invocation.getArgument(1), 2));

		var result = service().search(" 用户 ", null, 1, 2);

		assertThat(result.getContent()).extracting(response -> response.id()).containsExactly(middle.getId(), older.getId());
		assertThat(result.getTotalElements()).isEqualTo(5);
		assertThat(result.getNumber()).isEqualTo(1);
		assertThat(result.getSize()).isEqualTo(2);
	}

	@Test void resetsPasswordThroughDirectoryAndMatchingWriteStore(){MiniProgramUser user=new MiniProgramUser();user.setId("MINI-0123456789abcdef01234567");user.setStatus(UserStatus.ACTIVE);
		when(directory.findById(user.getId())).thenReturn(Optional.of(new IdentityUser(user.getId(),UserType.MINIPROGRAM,null,null,UserRole.COLLECTOR,UserStatus.ACTIVE,false,null,null)));
		when(mini.resetPasswordIfActive(org.mockito.ArgumentMatchers.eq(user.getId()),any(),any())).thenReturn(Optional.of(user));service().resetPassword(user.getId(),"Password-2");verify(sessions).revokeAll(user.getId());}

	@Test void disablesOnlyWebUsersAndRevokesTheirSessions(){WebUser user=new WebUser();user.setId("WEB-0123456789abcdef01234567");user.setRole(UserRole.REVIEWER);user.setStatus(UserStatus.ACTIVE);
		WebUser disabled=new WebUser();disabled.setId(user.getId());disabled.setRole(UserRole.REVIEWER);disabled.setStatus(UserStatus.DISABLED);
		when(web.findById(user.getId())).thenReturn(Optional.of(user));when(web.disableIfActive(org.mockito.ArgumentMatchers.eq(user.getId()),any())).thenReturn(Optional.of(disabled));
		assertThat(service().disable(user.getId()).status()).isEqualTo(UserStatus.DISABLED);verify(sessions).revokeAll(user.getId());}

	private WebUser webUser(String id, String username, Instant createdAt) {
		WebUser user = new WebUser(); user.setId(id); user.setUsername(username); user.setName("用户");
		user.setRole(UserRole.REVIEWER); user.setStatus(UserStatus.ACTIVE); user.setCreatedAt(createdAt); user.setUpdatedAt(createdAt);
		return user;
	}

	private MiniProgramUser miniUser(String id, String account, Instant createdAt) {
		MiniProgramUser user = new MiniProgramUser(); user.setId(id); user.setAccount(account); user.setName("用户");
		user.setStatus(UserStatus.ACTIVE); user.setCreatedAt(createdAt); user.setUpdatedAt(createdAt); return user;
	}
}

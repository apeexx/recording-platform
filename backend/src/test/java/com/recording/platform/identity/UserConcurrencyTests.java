package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserConcurrencyTests {
	@Test void concurrentDisableCannotBeOverwrittenByPasswordChange(){BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();WebUser active=new WebUser();active.setId("WEB-0123456789abcdef01234567");active.setRole(UserRole.ADMIN);active.setStatus(UserStatus.ACTIVE);active.setPasswordHash(passwords.encode("Password-1"));WebUser disabled=new WebUser();disabled.setId(active.getId());disabled.setStatus(UserStatus.DISABLED);
		WebUserStore users=mock(WebUserStore.class);when(users.findById(active.getId())).thenReturn(Optional.of(active),Optional.of(disabled));when(users.updatePasswordIfActive(any(),any(),any(),any())).thenReturn(false);
		WebAuthenticationService service=new WebAuthenticationService(users,mock(SessionService.class),passwords,Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"),ZoneOffset.UTC));
		assertThatThrownBy(()->service.changePassword(active.getId(),"Password-1","Password-2")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("ACCOUNT_DISABLED"));}
}

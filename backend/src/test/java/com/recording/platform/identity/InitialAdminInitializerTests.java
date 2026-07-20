package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

class InitialAdminInitializerTests {
	private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"),ZoneOffset.UTC);
	@Test void missingConfigurationDoesNothing(){WebUserStore users=mock(WebUserStore.class);new InitialAdminInitializer(users,new BCryptPasswordEncoder(),CLOCK,"","").run();verify(users,never()).save(any());}
	@Test void createsPrefixedFirstAdmin(){WebUserStore users=mock(WebUserStore.class);when(users.existsByRole(UserRole.ADMIN)).thenReturn(false);when(users.save(any())).thenAnswer(i->i.getArgument(0));
		new InitialAdminInitializer(users,new BCryptPasswordEncoder(),CLOCK,"root","Password-1").run();ArgumentCaptor<WebUser> captor=ArgumentCaptor.forClass(WebUser.class);verify(users).save(captor.capture());
		assertThat(captor.getValue().getId()).matches("WEB-[0-9a-f]{24}");assertThat(captor.getValue().isFirstPasswordChangeRequired()).isTrue();}
	@Test void invalidPasswordFailsClosed(){WebUserStore users=mock(WebUserStore.class);when(users.existsByRole(UserRole.ADMIN)).thenReturn(false);assertThatThrownBy(()->new InitialAdminInitializer(users,new BCryptPasswordEncoder(),CLOCK,"root","x".repeat(73)).run()).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("x".repeat(73));}
	@Test void existingAdminPreventsBootstrapWrite(){WebUserStore users=mock(WebUserStore.class);when(users.existsByRole(UserRole.ADMIN)).thenReturn(true);new InitialAdminInitializer(users,new BCryptPasswordEncoder(),CLOCK,"root","Password-1").run();verify(users,never()).save(any());}
	@Test void usernameCollisionWithoutAnAdminFailsClosed(){WebUserStore users=mock(WebUserStore.class);when(users.existsByRole(UserRole.ADMIN)).thenReturn(false);when(users.save(any())).thenThrow(new DuplicateKeyException("username"));
		assertThatThrownBy(()->new InitialAdminInitializer(users,new BCryptPasswordEncoder(),CLOCK,"root","Password-1").run()).isInstanceOf(IllegalStateException.class).hasMessageContaining("用户名冲突");}
}

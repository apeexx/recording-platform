package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.CollectorProfileGuard;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.security.PlatformPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CollectorProfileGuardTests {
	@Test void rejectsCollectorWhoseProfileIsIncomplete() {
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		UserAccount user = new UserAccount(); user.setId("c1"); user.setRole(UserRole.COLLECTOR); user.setName("张三");
		when(users.findById("c1")).thenReturn(Optional.of(user));
		var actor = new PlatformPrincipal("s","c1",null,"张三",UserRole.COLLECTOR,SessionType.MINIPROGRAM,false);
		assertThatThrownBy(() -> new CollectorProfileGuard(users).requireComplete(actor))
			.isInstanceOfSatisfying(ApiException.class, error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("PROFILE_INCOMPLETE"));
	}
}

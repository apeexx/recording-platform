package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.CollectorProfileGuard;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.security.PlatformPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CollectorProfileGuardTests {
	@Test void rejectsCollectorWhoseProfileIsIncomplete() {
		MiniProgramUserStore users = org.mockito.Mockito.mock(MiniProgramUserStore.class);
		MiniProgramUser user = new MiniProgramUser(); user.setId("MINI-0123456789abcdef01234567");
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
		var actor = new PlatformPrincipal("s",user.getId(),null,"张三",UserRole.COLLECTOR,SessionType.MINIPROGRAM,false);
		assertThatThrownBy(() -> new CollectorProfileGuard(users).requireComplete(actor))
			.isInstanceOfSatisfying(ApiException.class, error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("PROFILE_INCOMPLETE"));
	}
}

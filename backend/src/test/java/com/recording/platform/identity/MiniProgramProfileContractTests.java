package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.identity.dto.CollectorAccountLoginRequest;
import com.recording.platform.identity.dto.CompleteCollectorProfileRequest;
import com.recording.platform.identity.dto.MiniProgramSessionResponse;
import com.recording.platform.identity.dto.UpdateCollectorPasswordRequest;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.MiniProgramLoginResult;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MiniProgramProfileContractTests {
	@Test
	void requestContractsUseAccountAndNeverAcceptWechatIdentityFields() {
		assertThat(components(CollectorAccountLoginRequest.class)).containsExactly("account", "password");
		assertThat(components(CompleteCollectorProfileRequest.class))
			.containsExactly("name", "account", "password");
		assertThat(components(UpdateCollectorPasswordRequest.class))
			.containsExactly("currentPassword", "newPassword");
	}

	@Test
	void miniProgramSessionIncludesProfileAndAvatarStateWithoutStoragePaths() {
		UserAccount user = new UserAccount();
		user.setId("collector-1");
		user.setInternalUserNo("USR-000001");
		user.setUsername("682913");
		user.setName("张三");
		user.setPasswordHash("bcrypt-hash");
		user.setAvatarPath("collector-1.png");
		user.setAvatarContentType("image/png");
		user.setRole(UserRole.COLLECTOR);
		user.setStatus(UserStatus.ACTIVE);

		MiniProgramSessionResponse response = MiniProgramSessionResponse.from(
			new MiniProgramLoginResult("opaque-token", "session-1", user)
		);

		assertThat(response.account()).isEqualTo("682913");
		assertThat(response.profileComplete()).isTrue();
		assertThat(response.hasCustomAvatar()).isTrue();
		assertThat(components(MiniProgramSessionResponse.class)).doesNotContain("avatarPath", "passwordHash");
	}

	private java.util.List<String> components(Class<?> type) {
		return Arrays.stream(type.getRecordComponents()).map((component) -> component.getName()).toList();
	}
}

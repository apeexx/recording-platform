package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.service.IdentityIds;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class UserSplitContractTests {
	@Test
	void webAndMiniProgramUsersUseSeparateCollectionsAndFields() throws Exception {
		assertThat(WebUser.class.getAnnotation(Document.class).collection()).isEqualTo("web_users");
		assertThat(MiniProgramUser.class.getAnnotation(Document.class).collection()).isEqualTo("miniprogram_users");
		assertThat(WebUser.class.getDeclaredField("username").getAnnotation(Indexed.class).unique()).isTrue();
		assertThat(MiniProgramUser.class.getDeclaredField("account").getAnnotation(Indexed.class).unique()).isTrue();
		assertThat(fieldNames(WebUser.class)).doesNotContain(
			"internalUserNo", "account", "wechatAppId", "wechatOpenId", "avatarPath"
		);
		assertThat(fieldNames(MiniProgramUser.class)).doesNotContain(
			"internalUserNo", "username", "role", "firstPasswordChangeRequired"
		);
	}

	@Test
	void generatedIdsAndSessionTypesExposeTheNewIdentityBoundary() {
		assertThat(IdentityIds.web()).matches("WEB-[0-9a-f]{24}");
		assertThat(IdentityIds.miniProgram()).matches("MINI-[0-9a-f]{24}");
		assertThat(SessionType.values()).containsExactly(
			SessionType.WEB,
			SessionType.MINIPROGRAM,
			SessionType.WEB_TAKEOVER,
			SessionType.MINIPROGRAM_TAKEOVER
		);
	}

	@Test
	void splitStoresRequireBulkIdentityLookupImplementations() throws Exception {
		assertThat(WebUserStore.class.getMethod("findAllByIdIn", java.util.Collection.class).isDefault()).isFalse();
		assertThat(MiniProgramUserStore.class.getMethod("findAllByIdIn", java.util.Collection.class).isDefault()).isFalse();
	}

	private java.util.List<String> fieldNames(Class<?> type) {
		return Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList();
	}
}

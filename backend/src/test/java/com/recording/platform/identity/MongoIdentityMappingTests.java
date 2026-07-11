package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.UserAccount;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Version;

class MongoIdentityMappingTests {

	@Test
	void usersCollectionDefinesUniqueBackgroundAndWechatIdentityIndexes() throws Exception {
		assertThat(UserAccount.class.getAnnotation(Document.class).collection()).isEqualTo("users");
		assertUniqueIndex(UserAccount.class, "username", true);
		assertUniqueIndex(UserAccount.class, "internalUserNo", false);
		CompoundIndexes compoundIndexes = UserAccount.class.getAnnotation(CompoundIndexes.class);
		assertThat(Arrays.stream(compoundIndexes.value()).map(CompoundIndex::def).toList())
			.anyMatch((definition) -> definition.contains("wechatAppId") && definition.contains("wechatOpenId"));
		assertThat(Arrays.stream(compoundIndexes.value()).filter(CompoundIndex::unique).toList()).isNotEmpty();
	}

	@Test
	void sessionsCollectionUsesHashedTokensAndTtlExpiry() throws Exception {
		assertThat(SessionRecord.class.getAnnotation(Document.class).collection()).isEqualTo("sessions");
		assertUniqueIndex(SessionRecord.class, "tokenHash", false);
		Field expiresAt = SessionRecord.class.getDeclaredField("expiresAt");
		Indexed ttl = expiresAt.getAnnotation(Indexed.class);
		assertThat(ttl.expireAfter()).isEqualTo("0s");
		assertThat(expiresAt.getType()).isEqualTo(Instant.class);
		CompoundIndexes compoundIndexes = SessionRecord.class.getAnnotation(CompoundIndexes.class);
		assertThat(Arrays.stream(compoundIndexes.value()).map(CompoundIndex::def).toList())
			.anyMatch((definition) -> definition.contains("userId") && definition.contains("status"));
	}

	@Test
	void mutableUserAndSessionDocumentsUseOptimisticVersionsAsADefenseInDepth() throws Exception {
		assertThat(UserAccount.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
		assertThat(SessionRecord.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
	}

	private void assertUniqueIndex(Class<?> type, String fieldName, boolean sparse) throws Exception {
		Indexed indexed = type.getDeclaredField(fieldName).getAnnotation(Indexed.class);
		assertThat(indexed).isNotNull();
		assertThat(indexed.unique()).isTrue();
		assertThat(indexed.sparse()).isEqualTo(sparse);
	}
}

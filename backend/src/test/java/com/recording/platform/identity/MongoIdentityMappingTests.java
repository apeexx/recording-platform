package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.WebUser;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class MongoIdentityMappingTests {
	@Test void userCollectionsAndIndexesAreSeparated() throws Exception {assertThat(WebUser.class.getAnnotation(Document.class).collection()).isEqualTo("web_users");assertUnique(WebUser.class,"username",false);
		assertThat(MiniProgramUser.class.getAnnotation(Document.class).collection()).isEqualTo("miniprogram_users");assertUnique(MiniProgramUser.class,"account",true);
		assertThat(Arrays.stream(MiniProgramUser.class.getAnnotation(CompoundIndexes.class).value()).filter(CompoundIndex::unique).map(CompoundIndex::def).toList()).anyMatch(def->def.contains("wechatAppId")&&def.contains("wechatOpenId"));}
	@Test void sessionIndexesCoverBothActiveTypesAndTtl() throws Exception {CompoundIndexes indexes=SessionRecord.class.getAnnotation(CompoundIndexes.class);
		assertActiveIndex(indexes,"unique_active_web_session","WEB");assertActiveIndex(indexes,"unique_active_miniprogram_session","MINIPROGRAM");
		assertThat(SessionRecord.class.getDeclaredField("expiresAt").getAnnotation(Indexed.class).expireAfter()).isEqualTo("0s");assertThat(SessionRecord.class.getDeclaredField("expiresAt").getType()).isEqualTo(Instant.class);}
	@Test void mutableDocumentsUseVersions() throws Exception {assertThat(WebUser.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();assertThat(MiniProgramUser.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();assertThat(SessionRecord.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();}
	private void assertUnique(Class<?> type,String field,boolean sparse)throws Exception{Indexed index=type.getDeclaredField(field).getAnnotation(Indexed.class);assertThat(index.unique()).isTrue();assertThat(index.sparse()).isEqualTo(sparse);}
	private void assertActiveIndex(CompoundIndexes indexes,String name,String type){CompoundIndex index=Arrays.stream(indexes.value()).filter(candidate->candidate.name().equals(name)).findFirst().orElseThrow();
		assertThat(index.unique()).isTrue();assertThat(index.def().replace(" ","")).isEqualTo("{'userId':1,'type':1}");
		assertThat(index.partialFilter().replace(" ","")).isEqualTo("{'status':'ACTIVE','type':'"+type+"'}");}
}

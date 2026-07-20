package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.store.mongo.MongoMiniProgramUserStore;
import com.recording.platform.identity.store.mongo.MongoWebUserStore;
import com.recording.platform.identity.store.mongo.SpringDataMiniProgramUserRepository;
import com.recording.platform.identity.store.mongo.SpringDataWebUserRepository;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.PageRequest;
import java.util.List;

class MongoSplitUserStoreTests {
	@Test
	void firstPasswordChangeCasClearsTheMandatoryChangeFlag() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		when(mongo.updateFirst(any(Query.class), any(Update.class), eq(WebUser.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, null));
		MongoWebUserStore store = new MongoWebUserStore(mock(SpringDataWebUserRepository.class), mongo);

		assertThat(store.updatePasswordIfActive("WEB-0123456789abcdef01234567", "old", "new", Instant.EPOCH)).isTrue();

		ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
		verify(mongo).updateFirst(any(Query.class), update.capture(), eq(WebUser.class));
		Document set = (Document) update.getValue().getUpdateObject().get("$set");
		assertThat(set).containsEntry("passwordHash", "new").containsEntry("firstPasswordChangeRequired", false);
	}

	@Test
	void splitSearchesMatchFullPrefixedIdAsWellAsNameAndLoginName() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		when(mongo.count(any(Query.class), eq(WebUser.class))).thenReturn(0L);
		when(mongo.count(any(Query.class), eq(MiniProgramUser.class))).thenReturn(0L);
		when(mongo.find(any(Query.class), eq(WebUser.class))).thenReturn(List.of());
		when(mongo.find(any(Query.class), eq(MiniProgramUser.class))).thenReturn(List.of());
		String webId = "WEB-0123456789abcdef01234567";
		String miniId = "MINI-0123456789abcdef01234567";

		new MongoWebUserStore(mock(SpringDataWebUserRepository.class), mongo)
			.search(webId, null, PageRequest.of(0, 20));
		new MongoMiniProgramUserStore(mock(SpringDataMiniProgramUserRepository.class), mongo)
			.search(miniId, PageRequest.of(0, 20));

		ArgumentCaptor<Query> webQuery = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Query> miniQuery = ArgumentCaptor.forClass(Query.class);
		verify(mongo).find(webQuery.capture(), eq(WebUser.class));
		verify(mongo).find(miniQuery.capture(), eq(MiniProgramUser.class));
		assertThat(webQuery.getValue().getQueryObject().toJson()).contains("_id", webId, "name", "username");
		assertThat(miniQuery.getValue().getQueryObject().toJson()).contains("_id", miniId, "name", "account");
	}
}

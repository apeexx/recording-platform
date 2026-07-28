package com.recording.platform.identity.invitation.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.invitation.model.InvitationReservationResult;
import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoMiniProgramInvitationStoreTests {
	@Test
	void reservationUsesOneAtomicConditionalUpdateForStatusLimitAndIdentity() {
		SpringDataMiniProgramInvitationRepository repository =
			mock(SpringDataMiniProgramInvitationRepository.class);
		MongoTemplate mongo = mock(MongoTemplate.class);
		MongoMiniProgramInvitationStore store =
			new MongoMiniProgramInvitationStore(repository, mongo);
		MiniProgramInvitation reserved = new MiniProgramInvitation();
		reserved.setId("invite-1");
		when(mongo.exists(any(Query.class), eq(MiniProgramInvitation.class))).thenReturn(false);
		when(mongo.findAndModify(
			any(Query.class),
			any(Update.class),
			any(FindAndModifyOptions.class),
			eq(MiniProgramInvitation.class)
		)).thenReturn(reserved);

		InvitationReservationResult result = store.reserve(
			"invite-1",
			"identity-hash",
			Instant.parse("2026-07-28T08:00:00Z")
		);

		assertThat(result).isEqualTo(InvitationReservationResult.RESERVED);
		ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
		verify(mongo).findAndModify(
			queryCaptor.capture(),
			updateCaptor.capture(),
			any(FindAndModifyOptions.class),
			eq(MiniProgramInvitation.class)
		);
		Document query = queryCaptor.getValue().getQueryObject();
		assertThat(query.get("_id")).isEqualTo("invite-1");
		assertThat(query.get("status")).isEqualTo("ACTIVE");
		assertThat((Document) query.get("redemptionIdentityHashes"))
			.containsEntry("$ne", "identity-hash");
		assertThat((Document) query.get("$expr"))
			.containsEntry("$lt", List.of("$usedCount", "$maxUses"));
		Document update = updateCaptor.getValue().getUpdateObject();
		assertThat((Document) update.get("$inc")).containsEntry("usedCount", 1);
		assertThat((Document) update.get("$push"))
			.containsEntry("redemptionIdentityHashes", "identity-hash");
	}
}

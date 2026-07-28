package com.recording.platform.identity.invitation.store.mongo;

import com.recording.platform.identity.invitation.model.InvitationReservationResult;
import com.recording.platform.identity.invitation.model.InvitationStatus;
import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoMiniProgramInvitationStore implements MiniProgramInvitationStore {
	private final SpringDataMiniProgramInvitationRepository repository;
	private final MongoTemplate mongo;

	public MongoMiniProgramInvitationStore(
		SpringDataMiniProgramInvitationRepository repository,
		MongoTemplate mongo
	) {
		this.repository = repository;
		this.mongo = mongo;
	}

	@Override
	public MiniProgramInvitation save(MiniProgramInvitation invitation) {
		return repository.save(invitation);
	}

	@Override
	public Optional<MiniProgramInvitation> findById(String id) {
		return repository.findById(id);
	}

	@Override
	public Optional<MiniProgramInvitation> findByCodeHash(String codeHash) {
		return repository.findByCodeHash(codeHash);
	}

	@Override
	public Page<MiniProgramInvitation> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

	@Override
	public InvitationReservationResult reserve(String invitationId, String identityHash, Instant redeemedAt) {
		Query existing = Query.query(Criteria.where("_id").is(invitationId)
			.and("redemptionIdentityHashes").is(identityHash));
		if (mongo.exists(existing, MiniProgramInvitation.class)) {
			return InvitationReservationResult.ALREADY_RESERVED;
		}
		Document criteria = new Document("_id", invitationId)
			.append("status", InvitationStatus.ACTIVE.name())
			.append("redemptionIdentityHashes", new Document("$ne", identityHash))
			.append("$expr", new Document("$lt", List.of("$usedCount", "$maxUses")));
		Update update = new Update()
			.inc("usedCount", 1)
			.push("redemptionIdentityHashes", identityHash);
		MiniProgramInvitation reserved = mongo.findAndModify(
			new BasicQuery(criteria),
			update,
			FindAndModifyOptions.options().returnNew(true),
			MiniProgramInvitation.class
		);
		if (reserved != null) return InvitationReservationResult.RESERVED;
		return mongo.exists(existing, MiniProgramInvitation.class)
			? InvitationReservationResult.ALREADY_RESERVED
			: InvitationReservationResult.UNAVAILABLE;
	}

	@Override
	public Optional<MiniProgramInvitation> disable(
		String invitationId,
		String actorUserId,
		String actorName,
		Instant disabledAt
	) {
		Query query = Query.query(Criteria.where("_id").is(invitationId)
			.and("status").ne(InvitationStatus.DISABLED));
		Update update = new Update()
			.set("status", InvitationStatus.DISABLED)
			.set("disabledByUserId", actorUserId)
			.set("disabledByName", actorName)
			.set("disabledAt", disabledAt);
		return Optional.ofNullable(mongo.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			MiniProgramInvitation.class
		));
	}
}

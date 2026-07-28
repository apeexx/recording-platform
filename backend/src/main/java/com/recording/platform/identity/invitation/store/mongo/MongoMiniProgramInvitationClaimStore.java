package com.recording.platform.identity.invitation.store.mongo;

import com.recording.platform.identity.invitation.model.InvitationClaimResult;
import com.recording.platform.identity.invitation.model.MiniProgramInvitationClaim;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationClaimStore;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoMiniProgramInvitationClaimStore implements MiniProgramInvitationClaimStore {
	private final SpringDataMiniProgramInvitationClaimRepository repository;
	private final MongoTemplate mongo;

	public MongoMiniProgramInvitationClaimStore(
		SpringDataMiniProgramInvitationClaimRepository repository,
		MongoTemplate mongo
	) {
		this.repository = repository;
		this.mongo = mongo;
	}

	@Override
	public InvitationClaimResult claim(String identityHash, String invitationId, Instant createdAt) {
		MiniProgramInvitationClaim claim = new MiniProgramInvitationClaim();
		claim.setIdentityHash(identityHash);
		claim.setInvitationId(invitationId);
		claim.setCreatedAt(createdAt);
		try {
			repository.insert(claim);
			return InvitationClaimResult.ACQUIRED;
		} catch (DuplicateKeyException exception) {
			return repository.findById(identityHash)
				.filter(existing -> invitationId.equals(existing.getInvitationId()))
				.map(existing -> InvitationClaimResult.SAME_INVITATION)
				.orElse(InvitationClaimResult.OTHER_INVITATION);
		}
	}

	@Override
	public void release(String identityHash, String invitationId) {
		mongo.remove(Query.query(Criteria.where("_id").is(identityHash)
			.and("invitationId").is(invitationId)
			.and("userId").is(null)), MiniProgramInvitationClaim.class);
	}

	@Override
	public void complete(String identityHash, String userId, Instant completedAt) {
		mongo.updateFirst(
			Query.query(Criteria.where("_id").is(identityHash)),
			new Update().set("userId", userId).set("completedAt", completedAt),
			MiniProgramInvitationClaim.class
		);
	}
}

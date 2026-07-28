package com.recording.platform.identity.invitation.store.mongo;

import com.recording.platform.identity.invitation.model.MiniProgramInvitationClaim;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMiniProgramInvitationClaimRepository
	extends MongoRepository<MiniProgramInvitationClaim, String> {
}

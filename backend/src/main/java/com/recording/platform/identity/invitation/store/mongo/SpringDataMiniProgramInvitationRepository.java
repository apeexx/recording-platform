package com.recording.platform.identity.invitation.store.mongo;

import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMiniProgramInvitationRepository extends MongoRepository<MiniProgramInvitation, String> {
	Optional<MiniProgramInvitation> findByCodeHash(String codeHash);
}

package com.recording.platform.identity.invitation.store;

import com.recording.platform.identity.invitation.model.InvitationReservationResult;
import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MiniProgramInvitationStore {
	MiniProgramInvitation save(MiniProgramInvitation invitation);
	Optional<MiniProgramInvitation> findById(String id);
	Optional<MiniProgramInvitation> findByCodeHash(String codeHash);
	Page<MiniProgramInvitation> findAll(Pageable pageable);
	InvitationReservationResult reserve(String invitationId, String identityHash, Instant redeemedAt);
	Optional<MiniProgramInvitation> disable(
		String invitationId,
		String actorUserId,
		String actorName,
		Instant disabledAt
	);
}

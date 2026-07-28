package com.recording.platform.identity.invitation.store;

import com.recording.platform.identity.invitation.model.InvitationClaimResult;
import java.time.Instant;

public interface MiniProgramInvitationClaimStore {
	InvitationClaimResult claim(String identityHash, String invitationId, Instant createdAt);
	void release(String identityHash, String invitationId);
	void complete(String identityHash, String userId, Instant completedAt);
}

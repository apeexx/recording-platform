package com.recording.platform.identity.invitation;

import com.recording.platform.identity.invitation.model.InvitationEffectiveStatus;
import java.time.Instant;

public record MiniProgramInvitationView(
	String id,
	String name,
	String note,
	String codeSuffix,
	int maxUses,
	int usedCount,
	int remainingUses,
	InvitationEffectiveStatus status,
	String createdByUserId,
	String createdByName,
	Instant createdAt,
	String disabledByUserId,
	String disabledByName,
	Instant disabledAt
) {
}

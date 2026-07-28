package com.recording.platform.identity.dto;

import com.recording.platform.identity.invitation.MiniProgramInvitationCreated;
import com.recording.platform.identity.invitation.model.InvitationEffectiveStatus;
import java.time.Instant;

public record MiniProgramInvitationCreatedResponse(
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
	String invitationCode
) {
	public static MiniProgramInvitationCreatedResponse from(MiniProgramInvitationCreated created) {
		var view = created.view();
		return new MiniProgramInvitationCreatedResponse(
			view.id(), view.name(), view.note(), view.codeSuffix(),
			view.maxUses(), view.usedCount(), view.remainingUses(), view.status(),
			view.createdByUserId(), view.createdByName(), view.createdAt(),
			created.invitationCode()
		);
	}
}

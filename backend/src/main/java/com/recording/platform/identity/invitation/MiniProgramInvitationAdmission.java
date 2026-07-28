package com.recording.platform.identity.invitation;

import java.time.Instant;

public record MiniProgramInvitationAdmission(
	String invitationId,
	String invitationName,
	String invitationCodeSuffix,
	Instant invitationRedeemedAt
) {
}

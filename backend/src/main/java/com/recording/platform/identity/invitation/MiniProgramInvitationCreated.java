package com.recording.platform.identity.invitation;

public record MiniProgramInvitationCreated(
	MiniProgramInvitationView view,
	String invitationCode
) {
}

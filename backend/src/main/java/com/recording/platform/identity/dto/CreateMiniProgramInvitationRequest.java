package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMiniProgramInvitationRequest(
	@NotNull String name,
	String note,
	@NotNull Integer maxUses
) {
}

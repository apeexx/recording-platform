package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteCollectorProfileRequest(
	@NotBlank String name,
	@NotBlank String account,
	@NotBlank String password
) {
}

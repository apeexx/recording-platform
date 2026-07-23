package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteCollectorProfileRequest(
	@NotBlank String name,
	String account,
	String password
) {
}

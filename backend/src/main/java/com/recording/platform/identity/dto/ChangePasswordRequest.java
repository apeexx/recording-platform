package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangePasswordRequest(
	@NotBlank String currentPassword,
	@NotNull String newPassword
) {
}

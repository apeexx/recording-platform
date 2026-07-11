package com.recording.platform.identity.dto;

import com.recording.platform.identity.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBackendUserRequest(
	@NotBlank @Size(max = 64) String username,
	@NotBlank @Size(max = 64) String name,
	@NotNull UserRole role,
	@NotBlank @Size(min = 8, max = 128) String initialPassword
) {
}

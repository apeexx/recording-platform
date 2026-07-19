package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCollectorPasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}

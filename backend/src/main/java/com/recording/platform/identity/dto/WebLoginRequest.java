package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record WebLoginRequest(@NotBlank String username, @NotBlank String password) {
}

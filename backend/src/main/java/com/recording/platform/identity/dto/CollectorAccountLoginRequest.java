package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record CollectorAccountLoginRequest(@NotBlank String account, @NotBlank String password) {
}

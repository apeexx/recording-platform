package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotNull;

public record InitialPasswordRequest(@NotNull String newPassword) {
}

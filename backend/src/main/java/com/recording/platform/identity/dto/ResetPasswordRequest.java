package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotNull;

public record ResetPasswordRequest(@NotNull String newPassword) { }

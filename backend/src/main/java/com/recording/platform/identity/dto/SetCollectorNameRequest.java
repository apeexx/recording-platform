package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotNull;

public record SetCollectorNameRequest(@NotNull String name) {
}

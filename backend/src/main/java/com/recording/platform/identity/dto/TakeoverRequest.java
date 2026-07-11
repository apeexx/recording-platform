package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record TakeoverRequest(@NotBlank String takeoverToken) {
}

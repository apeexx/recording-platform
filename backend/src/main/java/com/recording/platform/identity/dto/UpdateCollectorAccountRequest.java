package com.recording.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCollectorAccountRequest(@NotBlank String account) { }

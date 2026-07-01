package com.recording.platform.voice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VoiceGenerationConfigRequest(
	@NotBlank String voiceId,
	@Min(0) @Max(3) double speed,
	@Min(0) @Max(5) double volume,
	@Min(-12) @Max(12) int pitch
) {
}

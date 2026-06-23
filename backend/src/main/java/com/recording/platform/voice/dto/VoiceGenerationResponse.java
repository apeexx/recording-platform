package com.recording.platform.voice.dto;

import com.recording.platform.voice.model.GenerationMode;
import com.recording.platform.voice.model.GenerationStatus;

public record VoiceGenerationResponse(
	String recordId,
	GenerationMode mode,
	GenerationStatus status,
	String message,
	String audioUrl
) {
}

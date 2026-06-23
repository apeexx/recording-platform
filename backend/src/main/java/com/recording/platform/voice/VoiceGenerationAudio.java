package com.recording.platform.voice;

import org.springframework.core.io.Resource;

public record VoiceGenerationAudio(Resource resource, String filename, String contentType) {
}

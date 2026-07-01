package com.recording.platform.voice.integration;

public record MiniMaxSynthesisResult(byte[] audioBytes, String format, long durationMillis) {
}

package com.recording.platform.task.model;

public record SubmittedRecording(
	String mediaId,
	String relativePath,
	RecordingFormat format,
	long sizeBytes,
	int sampleRate,
	int channels,
	long durationMillis
) {
}

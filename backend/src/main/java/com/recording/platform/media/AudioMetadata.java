package com.recording.platform.media;

import com.recording.platform.task.model.RecordingFormat;

record AudioMetadata(
	RecordingFormat format,
	int sampleRate,
	int channels,
	long durationMillis
) {
}

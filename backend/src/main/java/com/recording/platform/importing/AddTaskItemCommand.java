package com.recording.platform.importing;

public record AddTaskItemCommand(
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl
) {
}

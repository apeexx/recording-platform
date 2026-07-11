package com.recording.platform.importing;

public record AddTaskItemCommand(
	String externalItemId,
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl
) {
}

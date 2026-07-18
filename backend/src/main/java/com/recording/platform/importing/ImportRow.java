package com.recording.platform.importing;

public record ImportRow(
	long rowNumber,
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl
) {
}

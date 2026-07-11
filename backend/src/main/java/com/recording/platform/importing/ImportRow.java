package com.recording.platform.importing;

public record ImportRow(
	long rowNumber,
	String externalItemId,
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl
) {
}

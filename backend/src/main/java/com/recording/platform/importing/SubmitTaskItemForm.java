package com.recording.platform.importing;

public record SubmitTaskItemForm(
	String operationId,
	String assignmentId,
	long expectedRevision,
	String text,
	Long referenceAudioDurationMillis,
	Long referenceVideoDurationMillis
) {
	public SubmitTaskItemForm(String operationId, String assignmentId, long expectedRevision, String text) {
		this(operationId, assignmentId, expectedRevision, text, null, null);
	}
}

package com.recording.platform.importing;

public record SubmitTaskItemForm(
	String operationId,
	String assignmentId,
	long expectedRevision,
	String text
) {
}

package com.recording.platform.task.service;

import com.recording.platform.task.model.SubmittedRecording;

public record SubmitTaskItemCommand(
	String operationId,
	String assignmentId,
	long expectedRevision,
	String text,
	SubmittedRecording audio
) {
}

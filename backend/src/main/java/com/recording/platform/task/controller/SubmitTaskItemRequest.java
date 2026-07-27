package com.recording.platform.task.controller;

import com.recording.platform.importing.SubmitTaskItemForm;
import jakarta.validation.constraints.NotNull;

public record SubmitTaskItemRequest(
	@NotNull String operationId,
	@NotNull String assignmentId,
	@NotNull Long expectedRevision,
	String text,
	Long referenceAudioDurationMillis,
	Long referenceVideoDurationMillis
) {
	SubmitTaskItemForm toForm() {
		return new SubmitTaskItemForm(
			operationId,
			assignmentId,
			expectedRevision,
			text,
			referenceAudioDurationMillis,
			referenceVideoDurationMillis
		);
	}
}

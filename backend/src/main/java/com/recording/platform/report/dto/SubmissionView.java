package com.recording.platform.report.dto;

import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record SubmissionView(
	String itemId,
	String taskId,
	String operationId,
	Instant submittedAt,
	Long durationMillis,
	boolean textPresent,
	boolean audioPresent,
	String reviewConclusion,
	TaskItemStatus currentItemStatus
) { }

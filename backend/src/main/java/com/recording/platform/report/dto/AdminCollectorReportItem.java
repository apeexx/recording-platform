package com.recording.platform.report.dto;

import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record AdminCollectorReportItem(
	String itemId,
	String itemCode,
	Instant firstSubmittedAt,
	Instant latestSubmittedAt,
	Instant firstCompletedAt,
	TaskItemStatus currentItemStatus,
	long recordingDurationMillis,
	long referenceAudioDurationMillis,
	long referenceVideoDurationMillis,
	boolean textPresent,
	boolean audioPresent
) { }

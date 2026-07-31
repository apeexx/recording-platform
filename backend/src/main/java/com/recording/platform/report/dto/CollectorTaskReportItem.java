package com.recording.platform.report.dto;

import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public record CollectorTaskReportItem(
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
) {
	public CollectorTaskReportItem(
		String itemId,
		String itemCode,
		Instant firstSubmittedAt,
		Instant latestSubmittedAt,
		TaskItemStatus currentItemStatus,
		long recordingDurationMillis,
		long referenceAudioDurationMillis,
		long referenceVideoDurationMillis,
		boolean textPresent,
		boolean audioPresent
	) {
		this(
			itemId, itemCode, firstSubmittedAt, latestSubmittedAt, null, currentItemStatus,
			recordingDurationMillis, referenceAudioDurationMillis, referenceVideoDurationMillis,
			textPresent, audioPresent
		);
	}
}

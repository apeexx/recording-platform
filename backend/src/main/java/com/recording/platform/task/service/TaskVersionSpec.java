package com.recording.platform.task.service;

import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskResultType;
import java.util.List;
import java.util.Set;

public record TaskVersionSpec(
	Set<ReferenceType> referenceTypes,
	TaskResultType resultType,
	boolean humanReviewEnabled,
	RecordingFormat recordingFormat,
	Set<Integer> sampleRates,
	Integer channels,
	Long minDurationMillis,
	Long maxDurationMillis,
	List<String> rejectionReasons,
	boolean aiEnabled,
	String aiProvider,
	String aiModel
) {
	public boolean hasAudioConfiguration() {
		return recordingFormat != null || (sampleRates != null && !sampleRates.isEmpty()) || channels != null
			|| minDurationMillis != null || maxDurationMillis != null;
	}
}

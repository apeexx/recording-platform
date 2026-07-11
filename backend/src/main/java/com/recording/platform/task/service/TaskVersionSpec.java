package com.recording.platform.task.service;

import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import java.util.List;
import java.util.Set;

public record TaskVersionSpec(
	Set<ReferenceType> referenceTypes,
	boolean fixedRecording,
	boolean textInputEnabled,
	boolean humanReviewEnabled,
	RecordingFormat recordingFormat,
	Set<Integer> sampleRates,
	int channels,
	long minDurationMillis,
	long maxDurationMillis,
	List<String> rejectionReasons,
	boolean aiEnabled,
	String aiProvider,
	String aiModel
) {
}

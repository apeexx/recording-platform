package com.recording.platform.task.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskConfiguration {
	private Set<ReferenceType> referenceTypes = new LinkedHashSet<>();
	private TaskResultType resultType;
	private boolean humanReviewEnabled = true;
	private RecordingFormat recordingFormat;
	private Set<Integer> sampleRates = new LinkedHashSet<>();
	private int channels;
	private long minDurationMillis;
	private long maxDurationMillis;
	private List<String> rejectionReasons = new ArrayList<>();
	private boolean aiEnabled;
	private String aiProvider;
	private String aiModel;
}

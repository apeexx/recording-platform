package com.recording.platform.task.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "task_versions")
@CompoundIndexes({
	@CompoundIndex(name = "unique_task_version", def = "{'taskId': 1, 'versionNumber': 1}", unique = true)
})
public class TaskVersion {
	@Id
	private String id;
	@Version
	private Long version;
	private String taskId;
	private int versionNumber;
	private Set<ReferenceType> referenceTypes = new LinkedHashSet<>();
	private boolean fixedRecording;
	private boolean textInputEnabled;
	private boolean humanReviewEnabled = true;
	private RecordingFormat recordingFormat;
	private Set<Integer> sampleRates = new LinkedHashSet<>();
	private int channels = 1;
	private long minDurationMillis = 1000;
	private long maxDurationMillis = 600000;
	private List<String> rejectionReasons = new ArrayList<>();
	private boolean aiEnabled;
	private String aiProvider;
	private String aiModel;
	private boolean published;
	private Instant createdAt;
	private Instant publishedAt;
}

package com.recording.platform.task.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "tasks")
public class TaskRecord {
	@Id
	private String id;
	@Version
	private Long version;
	@Indexed(unique = true)
	private String taskCode;
	@Indexed
	private String platformId;
	private String name;
	private String description;
	private TaskLifecycle lifecycle;
	private String currentVersionId;
	private int currentVersionNumber;
	private long itemSequence;
	private Instant createdAt;
	private Instant updatedAt;
}

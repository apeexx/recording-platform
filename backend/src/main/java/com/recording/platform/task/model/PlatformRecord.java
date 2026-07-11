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
@Document(collection = "platforms")
public class PlatformRecord {
	@Id
	private String id;
	@Version
	private Long version;
	@Indexed(unique = true)
	private String code;
	private String name;
	private String description;
	private boolean active;
	private Instant createdAt;
	private Instant updatedAt;
}

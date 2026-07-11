package com.recording.platform.task.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "task_grants")
@CompoundIndexes({
	@CompoundIndex(name = "unique_task_grant", def = "{'taskId': 1, 'userId': 1}", unique = true)
})
public class TaskGrant {
	@Id
	private String id;
	@Version
	private Long version;
	private String taskId;
	private String userId;
	private GrantStatus status;
	private String grantedBy;
	private Instant createdAt;
	private Instant updatedAt;
}

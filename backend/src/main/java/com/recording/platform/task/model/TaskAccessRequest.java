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
@Document(collection = "task_access_requests")
@CompoundIndexes({
	@CompoundIndex(
		name = "unique_pending_task_access_request",
		def = "{'taskId': 1, 'userId': 1}",
		unique = true,
		partialFilter = "{'status': 'PENDING'}"
	),
	@CompoundIndex(name = "task_access_request_status", def = "{'taskId': 1, 'status': 1, 'createdAt': -1}")
})
public class TaskAccessRequest {
	@Id
	private String id;
	@Version
	private Long version;
	private String taskId;
	private String userId;
	private AccessRequestStatus status;
	private String decidedBy;
	private String decisionReason;
	private Instant createdAt;
	private Instant updatedAt;
}

package com.recording.platform.batch.model;

import com.recording.platform.identity.model.UserRole;
import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "batch_operation_jobs")
@CompoundIndexes({
	@CompoundIndex(name = "unique_batch_actor_operation", def = "{'actorUserId':1,'operationId':1}", unique = true),
	@CompoundIndex(name = "batch_actor_created", def = "{'actorUserId':1,'createdAt':-1}")
})
public class BatchOperationJob {
	@Id
	private String id;
	private String operationId;
	private String taskId;
	private BatchOperationSource source;
	private BatchOperationAction action;
	private TaskItemStatus targetStatus;
	private String reviewerId;
	private String actorUserId;
	private String actorName;
	private UserRole actorRole;
	private BatchOperationJobStatus status;
	private long selectedCount;
	private long applicableCount;
	private long processedCount;
	private long succeededCount;
	private long failedCount;
	private long skippedCount;
	private long nextSequence;
	private List<BatchOperationFailure> failures = new ArrayList<>();
	private String leaseOwner;
	private Instant leaseExpiresAt;
	private Instant heartbeatAt;
	private Instant createdAt;
	private Instant updatedAt;
	private Instant completedAt;
}

package com.recording.platform.review.ai;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "review_ai_jobs")
@CompoundIndexes({
	@CompoundIndex(
		name = "unique_review_ai_actor_operation",
		def = "{'actorUserId': 1, 'operationId': 1}",
		unique = true
	)
})
public class ReviewAiJob {
	@Id
	private String id;
	private String taskId;
	private String itemId;
	private long itemRevision;
	private String reviewAssignmentId;
	private String actorUserId;
	private String operationId;
	private ReviewAiJobType type;
	private ReviewAiJobStatus status;
	private ReviewAiStageConfig configSnapshot;
	private String sourceMediaId;
	private String resultText;
	private String model;
	private String requestId;
	private Long durationMillis;
	private String failureCode;
	private String failureMessage;
	private String leaseOwner;
	private String leaseToken;
	private Instant leaseExpiresAt;
	private Instant createdAt;
	private Instant updatedAt;
	@Indexed(expireAfter = "0s")
	private Instant expiresAt;
}

package com.recording.platform.review.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewAiJobRepository extends MongoRepository<ReviewAiJob, String> {
	Optional<ReviewAiJob> findByActorUserIdAndOperationId(String actorUserId, String operationId);
	List<ReviewAiJob> findTop20ByItemIdAndActorUserIdOrderByCreatedAtDesc(String itemId, String actorUserId);
	List<ReviewAiJob> findByStatusOrStatusAndLeaseExpiresAtBefore(
		ReviewAiJobStatus pending,
		ReviewAiJobStatus processing,
		Instant leaseExpiredBefore
	);
}

package com.recording.platform.idempotency;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataIdempotencyRecordRepository extends MongoRepository<IdempotencyRecord, String> {
	Optional<IdempotencyRecord> findByActorUserIdAndActionAndOperationKey(
		String actorUserId,
		String action,
		String operationKey
	);
	void deleteByActorUserIdAndActionAndOperationKey(String actorUserId, String action, String operationKey);
}

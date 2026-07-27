package com.recording.platform.batch.store.mongo;

import com.recording.platform.batch.model.BatchOperationJob;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataBatchOperationJobRepository extends MongoRepository<BatchOperationJob, String> {
	Optional<BatchOperationJob> findByActorUserIdAndOperationId(String actorUserId, String operationId);
}

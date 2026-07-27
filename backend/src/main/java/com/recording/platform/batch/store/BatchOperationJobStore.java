package com.recording.platform.batch.store;

import com.recording.platform.batch.model.BatchOperationJob;
import com.recording.platform.batch.model.BatchOperationSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BatchOperationJobStore {
	BatchOperationJob save(BatchOperationJob job);
	Optional<BatchOperationJob> findById(String id);
	Optional<BatchOperationJob> findByActorUserIdAndOperationId(String actorUserId, String operationId);
	default Optional<BatchOperationJob> acquireLease(
		String id, String workerId, Instant now, Instant expiresAt
	) { return Optional.empty(); }
	default Optional<BatchOperationJob> checkpoint(
		BatchOperationJob job, String workerId, Instant now, Instant expiresAt
	) { return Optional.of(save(job)); }
	default Optional<BatchOperationJob> finish(BatchOperationJob job, String workerId) {
		return Optional.of(save(job));
	}
	default List<BatchOperationJob> findRecoverable(Instant now) { return List.of(); }
	default List<BatchOperationJob> findRecent(
		String actorUserId, String taskId, BatchOperationSource source, int limit
	) { return List.of(); }
}

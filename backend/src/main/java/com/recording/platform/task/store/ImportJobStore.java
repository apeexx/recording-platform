package com.recording.platform.task.store;

import com.recording.platform.task.model.ImportJob;
import java.util.Optional;
import java.util.List;
import java.time.Instant;

public interface ImportJobStore {
	ImportJob save(ImportJob job);
	Optional<ImportJob> findById(String id);
	Optional<ImportJob> findByTaskIdAndOperationId(String taskId, String operationId);
	Optional<ImportJob> acquireLease(String jobId, String workerId, Instant now, Instant leaseExpiresAt);
	Optional<ImportJob> checkpoint(ImportJob job, String workerId, Instant now, Instant leaseExpiresAt);
	Optional<ImportJob> finish(ImportJob job, String workerId);
	List<ImportJob> findRecoverable(Instant now);
	default long cancelActiveByTaskId(String taskId, Instant now) { return 0; }
	default List<ImportJob> findAllByTaskId(String taskId) { return List.of(); }
	default void deleteAllByTaskId(String taskId) { }
}

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
	Optional<ImportJob> heartbeat(String jobId, String workerId, Instant now, Instant leaseExpiresAt);
	Optional<ImportJob> saveProgress(ImportJob job, String workerId);
	Optional<ImportJob> finish(ImportJob job, String workerId);
	List<ImportJob> findRecoverable(Instant now);
}

package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.ImportJob;
import java.util.Optional;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataImportJobRepository extends MongoRepository<ImportJob, String> {
	Optional<ImportJob> findByTaskIdAndOperationId(String taskId, String operationId);
	List<ImportJob> findAllByTaskId(String taskId);
	void deleteAllByTaskId(String taskId);
}

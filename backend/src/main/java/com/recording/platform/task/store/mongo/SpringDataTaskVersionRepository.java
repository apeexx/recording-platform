package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataTaskVersionRepository extends MongoRepository<TaskVersion, String> {
	Optional<TaskVersion> findByTaskIdAndVersionNumber(String taskId, int versionNumber);
	List<TaskVersion> findAllByTaskIdOrderByVersionNumber(String taskId);
}

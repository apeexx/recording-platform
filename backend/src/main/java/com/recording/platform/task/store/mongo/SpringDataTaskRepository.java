package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskRecord;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataTaskRepository extends MongoRepository<TaskRecord, String> {
	Optional<TaskRecord> findByTaskCode(String taskCode);
	List<TaskRecord> findAllByIdIn(Collection<String> ids);
	boolean existsByPlatformId(String platformId);
}

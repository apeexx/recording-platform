package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.recording.platform.task.model.TaskLifecycle;

interface SpringDataTaskRepository extends MongoRepository<TaskRecord, String> {
	Optional<TaskRecord> findByTaskCode(String taskCode);
	List<TaskRecord> findAllByIdIn(Collection<String> ids);
	Page<TaskRecord> findAllByLifecycleIn(Collection<TaskLifecycle> lifecycles, Pageable pageable);
}

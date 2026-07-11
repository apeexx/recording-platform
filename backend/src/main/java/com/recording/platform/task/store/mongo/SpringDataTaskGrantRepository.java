package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataTaskGrantRepository extends MongoRepository<TaskGrant, String> {
	Optional<TaskGrant> findByTaskIdAndUserId(String taskId, String userId);
	Optional<TaskGrant> findByTaskIdAndUserIdAndStatus(String taskId, String userId, GrantStatus status);
	Page<TaskGrant> findAllByTaskId(String taskId, Pageable pageable);
	Page<TaskGrant> findAllByUserIdAndStatus(String userId, GrantStatus status, Pageable pageable);
}

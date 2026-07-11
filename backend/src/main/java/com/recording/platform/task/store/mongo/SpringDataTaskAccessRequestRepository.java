package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataTaskAccessRequestRepository extends MongoRepository<TaskAccessRequest, String> {
	Optional<TaskAccessRequest> findFirstByTaskIdAndUserIdAndStatus(
		String taskId,
		String userId,
		AccessRequestStatus status
	);
	Page<TaskAccessRequest> findAllByTaskId(String taskId, Pageable pageable);
}

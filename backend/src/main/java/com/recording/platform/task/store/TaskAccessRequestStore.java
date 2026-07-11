package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.AccessRequestStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskAccessRequestStore {
	TaskAccessRequest save(TaskAccessRequest request);
	Optional<TaskAccessRequest> findById(String id);
	Optional<TaskAccessRequest> findPending(String taskId, String userId);
	Optional<TaskAccessRequest> decideIfPending(
		String requestId,
		AccessRequestStatus status,
		String decidedBy,
		String reason,
		Instant now
	);
	Page<TaskAccessRequest> findAllByTaskId(String taskId, Pageable pageable);
}

package com.recording.platform.task.store;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskGrantStore {
	TaskGrant save(TaskGrant grant);
	Optional<TaskGrant> findByTaskIdAndUserId(String taskId, String userId);
	Optional<TaskGrant> findActive(String taskId, String userId);
	Page<TaskGrant> findAllByTaskId(String taskId, Pageable pageable);
	default void deleteAllByTaskId(String taskId) { }
	default Page<TaskGrant> findAllActiveByUserId(String userId, Pageable pageable) { return Page.empty(pageable); }

	default TaskGrant activate(String taskId, String userId, String actorUserId, Instant now) {
		TaskGrant grant = findByTaskIdAndUserId(taskId, userId).orElseGet(TaskGrant::new);
		grant.setTaskId(taskId);
		grant.setUserId(userId);
		grant.setStatus(GrantStatus.ACTIVE);
		grant.setGrantedBy(actorUserId);
		if (grant.getCreatedAt() == null) grant.setCreatedAt(now);
		grant.setUpdatedAt(now);
		return save(grant);
	}
}

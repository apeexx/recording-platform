package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItem;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskItemStore {
	TaskItem save(TaskItem item);
	Optional<TaskItem> findById(String id);
	default Optional<TaskItem> findByTaskIdAndExternalItemId(String taskId, String externalItemId) {
		return Optional.empty();
	}
	default Optional<TaskItem> findByTaskIdAndCreationOperationId(String taskId, String operationId) {
		return Optional.empty();
	}
	Optional<TaskItem> findCurrentByCollector(String collectorId);
	Optional<TaskItem> claimAvailable(ClaimMutation mutation);
	Optional<TaskItem> submitIfCurrent(SubmitMutation mutation);
	Optional<TaskItem> rejectIfCurrent(RejectMutation mutation);
	Optional<TaskItem> releaseIfCurrent(ReleaseMutation mutation);
	Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable);
}

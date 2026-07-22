package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Collection;

interface SpringDataTaskItemRepository extends MongoRepository<TaskItem, String> {
	Optional<TaskItem> findByTaskIdAndCreationOperationId(String taskId, String creationOperationId);
	Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable);
	Page<TaskItem> findAllByStatusAndReviewerIdIsNull(TaskItemStatus status, Pageable pageable);
	Page<TaskItem> findAllByStatus(TaskItemStatus status, Pageable pageable);
	long countByTaskIdAndStatus(String taskId, TaskItemStatus status);
	Page<TaskItem> findAllByTaskIdAndStatus(String taskId, TaskItemStatus status, Pageable pageable);
	Page<TaskItem> findAllByTaskIdAndStatusAndReviewerIdIsNull(String taskId, TaskItemStatus status, Pageable pageable);
	Page<TaskItem> findAllByCollectorIdAndStatusIn(String collectorId, Collection<TaskItemStatus> statuses, Pageable pageable);
}

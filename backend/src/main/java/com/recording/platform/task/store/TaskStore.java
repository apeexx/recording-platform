package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskStore {
	TaskRecord save(TaskRecord task);
	void deleteById(String id);
	Optional<TaskRecord> findById(String id);
	Optional<TaskRecord> findByTaskCode(String taskCode);
	Page<TaskRecord> findAll(Pageable pageable);
	default List<TaskRecord> findAllByIdIn(Collection<String> ids) { return List.of(); }
	default boolean existsByPlatformId(String platformId) { return false; }
	long nextItemSequence(String taskId);
}

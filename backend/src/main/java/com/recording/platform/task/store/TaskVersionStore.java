package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskVersion;
import java.util.List;
import java.util.Optional;

public interface TaskVersionStore {
	TaskVersion save(TaskVersion version);
	void deleteById(String id);
	Optional<TaskVersion> findById(String id);
	Optional<TaskVersion> findByTaskIdAndVersionNumber(String taskId, int versionNumber);
	List<TaskVersion> findAllByTaskIdOrderByVersionNumber(String taskId);
}

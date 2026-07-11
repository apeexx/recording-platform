package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.store.TaskVersionStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MongoTaskVersionStore implements TaskVersionStore {
	private final SpringDataTaskVersionRepository repository;

	public MongoTaskVersionStore(SpringDataTaskVersionRepository repository) {
		this.repository = repository;
	}

	@Override public TaskVersion save(TaskVersion version) { return repository.save(version); }
	@Override public void deleteById(String id) { repository.deleteById(id); }
	@Override public Optional<TaskVersion> findById(String id) { return repository.findById(id); }
	@Override public Optional<TaskVersion> findByTaskIdAndVersionNumber(String taskId, int versionNumber) {
		return repository.findByTaskIdAndVersionNumber(taskId, versionNumber);
	}
	@Override public List<TaskVersion> findAllByTaskIdOrderByVersionNumber(String taskId) {
		return repository.findAllByTaskIdOrderByVersionNumber(taskId);
	}
}

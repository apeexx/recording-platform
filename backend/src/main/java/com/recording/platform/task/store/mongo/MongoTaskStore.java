package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskStore;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoTaskStore implements TaskStore {
	private final SpringDataTaskRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoTaskStore(SpringDataTaskRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override public TaskRecord save(TaskRecord task) { return repository.save(task); }
	@Override public void deleteById(String id) { repository.deleteById(id); }
	@Override public Optional<TaskRecord> findById(String id) { return repository.findById(id); }
	@Override public Optional<TaskRecord> findByTaskCode(String taskCode) { return repository.findByTaskCode(taskCode); }
	@Override public Page<TaskRecord> findAll(Pageable pageable) { return repository.findAll(pageable); }
	@Override public List<TaskRecord> findAllByIdIn(Collection<String> ids) { return repository.findAllByIdIn(ids); }

	@Override
	public long nextItemSequence(String taskId) {
		TaskRecord task = mongoTemplate.findAndModify(
			Query.query(Criteria.where("_id").is(taskId)),
			new Update().inc("itemSequence", 1),
			FindAndModifyOptions.options().returnNew(true),
			TaskRecord.class
		);
		return task == null ? 0 : task.getItemSequence();
	}
}

package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.store.TaskGrantStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoTaskGrantStore implements TaskGrantStore {
	private final SpringDataTaskGrantRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoTaskGrantStore(SpringDataTaskGrantRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override public TaskGrant save(TaskGrant grant) { return repository.save(grant); }
	@Override public Optional<TaskGrant> findByTaskIdAndUserId(String taskId, String userId) {
		return repository.findByTaskIdAndUserId(taskId, userId);
	}
	@Override public Optional<TaskGrant> findActive(String taskId, String userId) {
		return repository.findByTaskIdAndUserIdAndStatus(taskId, userId, GrantStatus.ACTIVE);
	}
	@Override public Page<TaskGrant> findAllByTaskId(String taskId, Pageable pageable) {
		return repository.findAllByTaskId(taskId, pageable);
	}
	@Override public Page<TaskGrant> findAllActiveByUserId(String userId, Pageable pageable) {
		return repository.findAllByUserIdAndStatus(userId, GrantStatus.ACTIVE, pageable);
	}

	@Override
	public TaskGrant activate(String taskId, String userId, String actorUserId, Instant now) {
		Query query = Query.query(Criteria.where("taskId").is(taskId).and("userId").is(userId));
		Update update = new Update()
			.set("status", GrantStatus.ACTIVE)
			.set("grantedBy", actorUserId)
			.set("updatedAt", now)
			.setOnInsert("taskId", taskId)
			.setOnInsert("userId", userId)
			.setOnInsert("createdAt", now);
		return mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().upsert(true).returnNew(true),
			TaskGrant.class
		);
	}
}

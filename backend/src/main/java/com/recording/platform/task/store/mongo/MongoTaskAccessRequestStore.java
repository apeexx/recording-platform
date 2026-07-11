package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.store.TaskAccessRequestStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoTaskAccessRequestStore implements TaskAccessRequestStore {
	private final SpringDataTaskAccessRequestRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoTaskAccessRequestStore(
		SpringDataTaskAccessRequestRepository repository,
		MongoTemplate mongoTemplate
	) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override public TaskAccessRequest save(TaskAccessRequest request) { return repository.save(request); }
	@Override public Optional<TaskAccessRequest> findById(String id) { return repository.findById(id); }
	@Override public Optional<TaskAccessRequest> findPending(String taskId, String userId) {
		return repository.findFirstByTaskIdAndUserIdAndStatus(taskId, userId, AccessRequestStatus.PENDING);
	}
	@Override
	public Optional<TaskAccessRequest> decideIfPending(
		String requestId,
		AccessRequestStatus status,
		String decidedBy,
		String reason,
		Instant now
	) {
		Query query = Query.query(Criteria.where("_id").is(requestId).and("status").is(AccessRequestStatus.PENDING));
		Update update = new Update()
			.set("status", status)
			.set("decidedBy", decidedBy)
			.set("updatedAt", now);
		if (reason == null) update.unset("decisionReason");
		else update.set("decisionReason", reason);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			TaskAccessRequest.class
		));
	}
	@Override public Page<TaskAccessRequest> findAllByTaskId(String taskId, Pageable pageable) {
		return repository.findAllByTaskId(taskId, pageable);
	}
}

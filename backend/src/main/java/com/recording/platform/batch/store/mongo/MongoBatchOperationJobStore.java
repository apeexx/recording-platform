package com.recording.platform.batch.store.mongo;

import com.recording.platform.batch.model.BatchOperationJob;
import com.recording.platform.batch.model.BatchOperationJobStatus;
import com.recording.platform.batch.model.BatchOperationSource;
import com.recording.platform.batch.store.BatchOperationJobStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class MongoBatchOperationJobStore implements BatchOperationJobStore {
	private final SpringDataBatchOperationJobRepository repository;
	private final MongoTemplate mongo;
	MongoBatchOperationJobStore(SpringDataBatchOperationJobRepository repository, MongoTemplate mongo) {
		this.repository = repository;
		this.mongo = mongo;
	}
	@Override public BatchOperationJob save(BatchOperationJob job) { return repository.save(job); }
	@Override public Optional<BatchOperationJob> findById(String id) { return repository.findById(id); }
	@Override public Optional<BatchOperationJob> findByActorUserIdAndOperationId(String actorUserId, String operationId) {
		return repository.findByActorUserIdAndOperationId(actorUserId, operationId);
	}
	@Override public Optional<BatchOperationJob> acquireLease(
		String id, String workerId, Instant now, Instant expiresAt
	) {
		Criteria recoverable = new Criteria().orOperator(
			Criteria.where("status").is(BatchOperationJobStatus.PENDING),
			new Criteria().andOperator(
				Criteria.where("status").is(BatchOperationJobStatus.PROCESSING),
				Criteria.where("leaseExpiresAt").lt(now)
			)
		);
		Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(id), recoverable));
		Update update = new Update().set("status", BatchOperationJobStatus.PROCESSING)
			.set("leaseOwner", workerId).set("leaseExpiresAt", expiresAt)
			.set("heartbeatAt", now).set("updatedAt", now);
		return Optional.ofNullable(mongo.findAndModify(query, update,
			org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), BatchOperationJob.class));
	}
	@Override public Optional<BatchOperationJob> checkpoint(
		BatchOperationJob job, String workerId, Instant now, Instant expiresAt
	) {
		Query query = Query.query(Criteria.where("_id").is(job.getId())
			.and("status").is(BatchOperationJobStatus.PROCESSING).and("leaseOwner").is(workerId));
		Update update = counters(job).set("heartbeatAt", now).set("leaseExpiresAt", expiresAt).set("updatedAt", now);
		return Optional.ofNullable(mongo.findAndModify(query, update,
			org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), BatchOperationJob.class));
	}
	@Override public Optional<BatchOperationJob> finish(BatchOperationJob job, String workerId) {
		Query query = Query.query(Criteria.where("_id").is(job.getId()).and("leaseOwner").is(workerId));
		Update update = counters(job).set("status", job.getStatus()).set("completedAt", job.getCompletedAt())
			.set("updatedAt", job.getUpdatedAt()).unset("leaseOwner").unset("leaseExpiresAt");
		return Optional.ofNullable(mongo.findAndModify(query, update,
			org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), BatchOperationJob.class));
	}
	@Override public List<BatchOperationJob> findRecoverable(Instant now) {
		Criteria criteria = new Criteria().orOperator(
			Criteria.where("status").is(BatchOperationJobStatus.PENDING),
			new Criteria().andOperator(Criteria.where("status").is(BatchOperationJobStatus.PROCESSING),
				Criteria.where("leaseExpiresAt").lt(now))
		);
		return mongo.find(Query.query(criteria).limit(100), BatchOperationJob.class);
	}
	@Override public List<BatchOperationJob> findRecent(
		String actorUserId, String taskId, BatchOperationSource source, int limit
	) {
		Criteria criteria = Criteria.where("actorUserId").is(actorUserId);
		if (taskId != null && !taskId.isBlank()) criteria = criteria.and("taskId").is(taskId);
		if (source != null) criteria = criteria.and("source").is(source);
		return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "createdAt"))
			.limit(Math.min(Math.max(limit, 1), 20)), BatchOperationJob.class);
	}
	private Update counters(BatchOperationJob job) {
		return new Update().set("processedCount", job.getProcessedCount())
			.set("succeededCount", job.getSucceededCount()).set("failedCount", job.getFailedCount())
			.set("skippedCount", job.getSkippedCount()).set("nextSequence", job.getNextSequence())
			.set("failures", job.getFailures());
	}
}

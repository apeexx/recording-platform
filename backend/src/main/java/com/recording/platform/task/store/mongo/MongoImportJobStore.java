package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.store.ImportJobStore;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import com.recording.platform.task.model.ImportJobStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoImportJobStore implements ImportJobStore {
	private final SpringDataImportJobRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoImportJobStore(SpringDataImportJobRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}
	@Override public ImportJob save(ImportJob job) { return repository.save(job); }
	@Override public Optional<ImportJob> findById(String id) { return repository.findById(id); }
	@Override public Optional<ImportJob> findByTaskIdAndOperationId(String taskId, String operationId) {
		return repository.findByTaskIdAndOperationId(taskId, operationId);
	}

	@Override
	public Optional<ImportJob> acquireLease(
		String jobId,
		String workerId,
		Instant now,
		Instant leaseExpiresAt
	) {
		Criteria recoverable = new Criteria().orOperator(
			Criteria.where("status").is(ImportJobStatus.PENDING),
			new Criteria().andOperator(
				Criteria.where("status").is(ImportJobStatus.PROCESSING),
				leaseExpired(now)
			)
		);
		Query query = Query.query(new Criteria().andOperator(Criteria.where("_id").is(jobId), recoverable));
		Update update = new Update()
			.set("status", ImportJobStatus.PROCESSING)
			.set("leaseOwner", workerId)
			.set("leaseExpiresAt", leaseExpiresAt)
			.set("heartbeatAt", now)
			.set("startedAt", now)
			.set("updatedAt", now)
			.inc("attempt", 1);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			ImportJob.class
		));
	}

	@Override
	public boolean heartbeat(String jobId, String workerId, Instant now, Instant leaseExpiresAt) {
		Query query = Query.query(Criteria.where("_id").is(jobId)
			.and("status").is(ImportJobStatus.PROCESSING)
			.and("leaseOwner").is(workerId));
		Update update = new Update()
			.set("heartbeatAt", now)
			.set("leaseExpiresAt", leaseExpiresAt)
			.set("updatedAt", now);
		return mongoTemplate.updateFirst(query, update, ImportJob.class).getModifiedCount() == 1;
	}

	@Override
	public List<ImportJob> findRecoverable(Instant now) {
		Criteria criteria = new Criteria().orOperator(
			Criteria.where("status").is(ImportJobStatus.PENDING),
			new Criteria().andOperator(
				Criteria.where("status").is(ImportJobStatus.PROCESSING),
				leaseExpired(now)
			)
		);
		return mongoTemplate.find(Query.query(criteria), ImportJob.class);
	}

	private Criteria leaseExpired(Instant now) {
		return new Criteria().orOperator(
			Criteria.where("leaseExpiresAt").lte(now),
			Criteria.where("leaseExpiresAt").exists(false),
			Criteria.where("leaseExpiresAt").is(null)
		);
	}
}

package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.store.TaskGrantStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
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
	@Override public Page<TaskGrant> search(
		String taskId, GrantStatus status, String queryText, Pageable pageable
	) {
		if (queryText == null || queryText.isBlank()) {
			Criteria criteria = Criteria.where("taskId").is(taskId);
			if (status != null) criteria = criteria.and("status").is(status);
			Query query = Query.query(criteria);
			long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskGrant.class);
			query.with(pageable);
			return new PageImpl<>(mongoTemplate.find(query, TaskGrant.class), pageable, total);
		}
		Criteria criteria = Criteria.where("taskId").is(taskId);
		if (status != null) criteria = criteria.and("status").is(status);
		String term = queryText.trim();
		String safe = Pattern.quote(term);
		Aggregation aggregation = Aggregation.newAggregation(
			Aggregation.match(criteria),
			Aggregation.lookup("miniprogram_users", "userId", "_id", "user"),
			Aggregation.unwind("user", true),
			Aggregation.match(new Criteria().orOperator(
				Criteria.where("user.name").regex(safe, "i"),
				Criteria.where("user.account").regex(safe, "i"),
				Criteria.where("userId").is(term)
			)),
			Aggregation.facet(
				Aggregation.skip(pageable.getOffset()),
				Aggregation.limit(pageable.getPageSize())
			).as("items").and(Aggregation.count().as("value")).as("totals")
		);
		return aggregatePage(aggregation, pageable);
	}
	@Override public Page<TaskGrant> findAllActiveByUserId(String userId, Pageable pageable) {
		return repository.findAllByUserIdAndStatus(userId, GrantStatus.ACTIVE, pageable);
	}
	@Override public void deleteAllByTaskId(String taskId) { repository.deleteAllByTaskId(taskId); }

	@SuppressWarnings("unchecked")
	private Page<TaskGrant> aggregatePage(Aggregation aggregation, Pageable pageable) {
		Document result = mongoTemplate.aggregate(aggregation, "task_grants", Document.class)
			.getUniqueMappedResult();
		if (result == null) return Page.empty(pageable);
		List<Document> itemDocuments = (List<Document>) result.getOrDefault("items", List.of());
		List<Document> totals = (List<Document>) result.getOrDefault("totals", List.of());
		long total = totals.isEmpty() ? 0L : ((Number) totals.get(0).getOrDefault("value", 0)).longValue();
		List<TaskGrant> items = itemDocuments.stream()
			.map(document -> mongoTemplate.getConverter().read(TaskGrant.class, document)).toList();
		return new PageImpl<>(items, pageable, total);
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

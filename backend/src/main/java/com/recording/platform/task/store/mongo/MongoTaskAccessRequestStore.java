package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.store.TaskAccessRequestStore;
import java.util.Optional;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
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
	@Override public void deleteAllByTaskId(String taskId) { repository.deleteAllByTaskId(taskId); }
	@Override public Page<TaskAccessRequest> findAllByTaskIdAndStatus(
		String taskId, AccessRequestStatus status, Pageable pageable
	) { return repository.findAllByTaskIdAndStatus(taskId, status, pageable); }
	@Override public Page<TaskAccessRequest> searchPending(
		String taskId, String queryText, Pageable pageable
	) {
		Criteria criteria = Criteria.where("taskId").is(taskId)
			.and("status").is(AccessRequestStatus.PENDING);
		if (queryText == null || queryText.isBlank()) {
			Query query = Query.query(criteria);
			long total = mongoTemplate.count(
				Query.of(query).limit(-1).skip(-1), TaskAccessRequest.class
			);
			query.with(pageable);
			return new PageImpl<>(
				mongoTemplate.find(query, TaskAccessRequest.class), pageable, total
			);
		}
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

	@SuppressWarnings("unchecked")
	private Page<TaskAccessRequest> aggregatePage(Aggregation aggregation, Pageable pageable) {
		Document result = mongoTemplate.aggregate(
			aggregation, "task_access_requests", Document.class
		).getUniqueMappedResult();
		if (result == null) return Page.empty(pageable);
		List<Document> itemDocuments = (List<Document>) result.getOrDefault("items", List.of());
		List<Document> totals = (List<Document>) result.getOrDefault("totals", List.of());
		long total = totals.isEmpty() ? 0L : ((Number) totals.get(0).getOrDefault("value", 0)).longValue();
		List<TaskAccessRequest> items = itemDocuments.stream()
			.map(document -> mongoTemplate.getConverter().read(TaskAccessRequest.class, document)).toList();
		return new PageImpl<>(items, pageable, total);
	}
}

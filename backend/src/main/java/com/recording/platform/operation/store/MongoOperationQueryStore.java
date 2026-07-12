package com.recording.platform.operation.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
public class MongoOperationQueryStore implements OperationQueryStore {
	private final MongoTemplate mongoTemplate;

	public MongoOperationQueryStore(MongoTemplate mongoTemplate) { this.mongoTemplate = mongoTemplate; }

	@Override
	public Page<OperationEntry> findOperations(String actorUserId, Pageable pageable) {
		List<AggregationOperation> base = new ArrayList<>();
		base.add(Aggregation.unwind("operations"));
		if (actorUserId != null) {
			base.add(Aggregation.match(Criteria.where("operations.actorUserId").is(actorUserId)));
		}
		List<AggregationOperation> pagePipeline = new ArrayList<>(base);
		pagePipeline.add(Aggregation.sort(Sort.Direction.DESC, "operations.occurredAt"));
		pagePipeline.add(Aggregation.skip(pageable.getOffset()));
		pagePipeline.add(Aggregation.limit(pageable.getPageSize()));
		pagePipeline.add(Aggregation.project()
			.and("_id").as("itemId")
			.and("operations.actorUserId").as("actorUserId")
			.and("operations.actorUsername").as("actorUsername")
			.and("operations.content").as("content")
			.and("operations.occurredAt").as("occurredAt"));
		List<OperationEntry> entries = mongoTemplate.aggregate(
			Aggregation.newAggregation(pagePipeline), "task_items", Document.class
		).getMappedResults().stream().map(this::entry).toList();

		List<AggregationOperation> countPipeline = new ArrayList<>(base);
		countPipeline.add(Aggregation.count().as("total"));
		Document count = mongoTemplate.aggregate(
			Aggregation.newAggregation(countPipeline), "task_items", Document.class
		).getUniqueMappedResult();
		long total = count == null ? 0 : ((Number) count.getOrDefault("total", 0)).longValue();
		return new PageImpl<>(entries, pageable, total);
	}

	private OperationEntry entry(Document document) {
		Object value = document.get("occurredAt");
		Instant occurredAt = value instanceof Date date ? date.toInstant()
			: value instanceof Instant instant ? instant : null;
		return new OperationEntry(
			document.getString("itemId"), document.getString("actorUserId"),
			document.getString("actorUsername"), document.getString("content"), occurredAt
		);
	}
}

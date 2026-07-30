package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class TaskItemCompletionMigrationService {
	private static final List<String> COMPLETION_OPERATION_TYPES = List.of(
		"REVIEW_APPROVE", "ADMIN_BATCH_APPROVE", "SUBMIT", "ADMIN_STATUS_CHANGE"
	);

	private final MongoTemplate mongo;

	public TaskItemCompletionMigrationService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public Result backfillFirstCompletedAt() {
		Criteria missingCompletion = new Criteria().orOperator(
			Criteria.where("firstCompletedAt").exists(false),
			Criteria.where("firstCompletedAt").is(null)
		);
		Criteria realCompletion = Criteria.where("operations").elemMatch(new Criteria().andOperator(
			Criteria.where("type").in(COMPLETION_OPERATION_TYPES),
			Criteria.where("resultStatus").is(TaskItemStatus.COMPLETED),
			Criteria.where("occurredAt").exists(true)
		));
		Query candidates = Query.query(new Criteria().andOperator(missingCompletion, realCompletion));
		AggregationExpression earliestCompletion = context -> new Document("$min",
			new Document("$map", new Document()
				.append("input", new Document("$filter", new Document()
					.append("input", new Document("$ifNull", List.of("$operations", List.of())))
					.append("as", "operation")
					.append("cond", new Document("$and", List.of(
						new Document("$in", List.of("$$operation.type", COMPLETION_OPERATION_TYPES)),
						new Document("$eq", List.of("$$operation.resultStatus", "COMPLETED")),
						new Document("$ne", java.util.Arrays.asList("$$operation.occurredAt", null))
					)))))
				.append("as", "operation")
				.append("in", "$$operation.occurredAt"))
		);
		AggregationUpdate update = AggregationUpdate.update()
			.set("firstCompletedAt").toValue(earliestCompletion);
		long migrated = mongo.updateMulti(candidates, update, TaskItem.class).getModifiedCount();

		Criteria stillMissing = new Criteria().orOperator(
			Criteria.where("firstCompletedAt").exists(false),
			Criteria.where("firstCompletedAt").is(null)
		);
		Query unresolved = Query.query(new Criteria().andOperator(
			stillMissing,
			Criteria.where("operations.resultStatus").is(TaskItemStatus.COMPLETED)
		));
		return new Result(migrated, mongo.count(unresolved, TaskItem.class));
	}

	public record Result(long migrated, long unresolved) { }
}

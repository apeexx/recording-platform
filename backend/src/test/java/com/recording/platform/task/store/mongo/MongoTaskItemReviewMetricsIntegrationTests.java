package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ReviewTaskMetrics;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoTaskItemReviewMetricsIntegrationTests {

	@Test
	void aggregatesReviewMetricsAgainstRealMongoExpressions() {
		String databaseName = "review_metrics_test_"
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
			MongoTemplate template = new MongoTemplate(client, databaseName);
			var collection = template.getCollection("task_items");
			Instant submittedAt = Instant.parse("2026-07-30T01:00:00Z");
			Instant completedAt = Instant.parse("2026-07-30T02:00:00Z");
			collection.insertMany(List.of(
				item("available", TaskItemStatus.AVAILABLE, null, null),
				item("submitted", TaskItemStatus.SUBMITTED, submittedAt, null),
				item("review-pending", TaskItemStatus.REVIEW_PENDING, submittedAt, null),
				item("completed", TaskItemStatus.COMPLETED, submittedAt, completedAt),
				item("rework", TaskItemStatus.REWORK_PENDING, submittedAt, null),
				item("discarded", TaskItemStatus.DISCARDED, submittedAt, null)
			));
			MongoTaskItemStore store = new MongoTaskItemStore(
				org.mockito.Mockito.mock(SpringDataTaskItemRepository.class),
				template
			);

			List<ReviewTaskMetrics> result = store.reviewTaskMetrics(
				List.of("task-1"),
				Instant.parse("2026-07-29T16:00:00Z"),
				Instant.parse("2026-07-30T16:00:00Z")
			);

			assertThat(result).containsExactly(new ReviewTaskMetrics(
				"task-1", 5, 1, 4, 2, 1, 1, 1
			));
		} finally {
			try (MongoClient cleanup = MongoClients.create("mongodb://localhost:27017")) {
				cleanup.getDatabase(databaseName).drop();
			}
		}
	}

	private Document item(
		String id,
		TaskItemStatus status,
		Instant firstSubmittedAt,
		Instant firstCompletedAt
	) {
		Document item = new Document("_id", id)
			.append("taskId", "task-1")
			.append("status", status.name());
		if (firstSubmittedAt != null) {
			item.append("firstSubmittedAt", Date.from(firstSubmittedAt));
		}
		if (firstCompletedAt != null) {
			item.append("firstCompletedAt", Date.from(firstCompletedAt));
		}
		return item;
	}
}

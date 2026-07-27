package com.recording.platform.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.recording.platform.report.store.MongoReportQueryStore;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoReportQueryStoreTests {
	@Test
	void collectorSubmissionProjectionSupportsMissingTextAndAudio() {
		MongoTemplate template = mock(MongoTemplate.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> items = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> tasks = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> pageRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> countRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> emptyCursor = mock(MongoCursor.class);

		when(template.getCollection("task_items")).thenReturn(items);
		when(template.getCollection("tasks")).thenReturn(tasks);
		when(items.aggregate(anyList())).thenReturn(pageRows, countRows);
		when(pageRows.iterator()).thenReturn(emptyCursor);
		when(emptyCursor.hasNext()).thenReturn(false);
		when(countRows.first()).thenReturn(null);

		var result = new MongoReportQueryStore(template).findCollectorTaskSubmissions(
			"MINI-1", "task-1", PageRequest.of(0, 20)
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void collectorTaskListResolvesStringTaskIdAsMongoObjectId() {
		MongoTemplate template = mock(MongoTemplate.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> items = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> tasks = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> rows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> cursor = mock(MongoCursor.class);
		@SuppressWarnings("unchecked")
		FindIterable<Document> taskRows = mock(FindIterable.class);
		String taskId = "507f1f77bcf86cd799439011";

		when(template.getCollection("task_items")).thenReturn(items);
		when(template.getCollection("tasks")).thenReturn(tasks);
		when(items.aggregate(anyList())).thenReturn(rows);
		when(rows.iterator()).thenReturn(cursor);
		when(cursor.hasNext()).thenReturn(true, false);
		when(cursor.next()).thenReturn(new Document("_id", taskId)
			.append("latestSubmittedAt", java.util.Date.from(java.time.Instant.parse("2026-07-27T08:00:00Z"))));
		when(tasks.find(any(Document.class))).thenReturn(taskRows);
		when(taskRows.first()).thenReturn(new Document("_id", new ObjectId(taskId))
			.append("taskCode", "T000001").append("name", "普通话任务"));

		var result = new MongoReportQueryStore(template).findCollectorTasks("MINI-1");

		assertEquals(1, result.size());
		assertEquals("T000001", result.get(0).taskCode());
		ArgumentCaptor<Document> query = ArgumentCaptor.forClass(Document.class);
		verify(tasks).find(query.capture());
		assertEquals(new ObjectId(taskId), query.getValue().get("_id"));
	}
}

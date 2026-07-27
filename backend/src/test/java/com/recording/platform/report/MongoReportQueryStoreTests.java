package com.recording.platform.report;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.recording.platform.report.store.MongoReportQueryStore;
import org.bson.Document;
import org.junit.jupiter.api.Test;
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
}

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
import java.util.List;

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

	@Test
	void adminStageSummaryUsesIndependentDatesAndReturnsAllShanghaiHours() {
		MongoTemplate template = mock(MongoTemplate.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> items = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> tasks = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> submissionRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> completionRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> hourRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> hourCursor = mock(MongoCursor.class);
		when(template.getCollection("task_items")).thenReturn(items);
		when(template.getCollection("tasks")).thenReturn(tasks);
		when(items.aggregate(anyList())).thenReturn(submissionRows, completionRows, hourRows);
		when(submissionRows.first()).thenReturn(new Document("count", 3));
		when(completionRows.first()).thenReturn(new Document("count", 2));
		when(hourRows.iterator()).thenReturn(hourCursor);
		when(hourCursor.hasNext()).thenReturn(true, false);
		when(hourCursor.next()).thenReturn(new Document("_id", 9).append("count", 2));

		var result = new MongoReportQueryStore(template).aggregateAdminStages(
			"task-1", null,
			java.time.Instant.parse("2026-07-26T16:00:00Z"),
			java.time.Instant.parse("2026-07-27T16:00:00Z")
		);

		assertEquals(3, result.submissions().count());
		assertEquals(2, result.completions().count());
		assertEquals(24, result.submissionHourDistribution().size());
		assertEquals(2, result.submissionHourDistribution().get(9).count());
		@SuppressWarnings("rawtypes")
		ArgumentCaptor<List> pipelines = ArgumentCaptor.forClass(List.class);
		verify(items, org.mockito.Mockito.times(3)).aggregate(pipelines.capture());
		String submissionPipeline = pipelines.getAllValues().get(0).toString();
		String completionPipeline = pipelines.getAllValues().get(1).toString();
		String hourPipeline = pipelines.getAllValues().get(2).toString();
		assertTrue(submissionPipeline.contains("firstSubmittedAt"));
		assertTrue(!submissionPipeline.contains("firstCompletedAt"));
		assertTrue(completionPipeline.contains("firstCompletedAt"));
		assertTrue(completionPipeline.contains("COMPLETED"));
		assertTrue(hourPipeline.contains("Asia/Shanghai"));
	}

	@Test
	void timestampRankingKeepsCompletionOnlyCollectorsAfterNonNullSubmissionTimes() {
		MongoTemplate template = mock(MongoTemplate.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> items = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		MongoCollection<Document> tasks = mock(MongoCollection.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> submissionRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> hourRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		AggregateIterable<Document> completionRows = mock(AggregateIterable.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> submissionCursor = mock(MongoCursor.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> emptyHourCursor = mock(MongoCursor.class);
		@SuppressWarnings("unchecked")
		MongoCursor<Document> completionCursor = mock(MongoCursor.class);
		when(template.getCollection("task_items")).thenReturn(items);
		when(template.getCollection("tasks")).thenReturn(tasks);
		when(items.aggregate(anyList())).thenReturn(submissionRows, hourRows, completionRows);
		when(submissionRows.iterator()).thenReturn(submissionCursor);
		when(submissionCursor.hasNext()).thenReturn(true, false);
		when(submissionCursor.next()).thenReturn(new Document("_id", "collector-with-time")
			.append("count", 1)
			.append("firstSubmissionAt", java.util.Date.from(java.time.Instant.parse("2026-07-30T01:00:00Z")))
			.append("latestSubmissionAt", java.util.Date.from(java.time.Instant.parse("2026-07-30T02:00:00Z"))));
		when(hourRows.iterator()).thenReturn(emptyHourCursor);
		when(emptyHourCursor.hasNext()).thenReturn(false);
		when(completionRows.iterator()).thenReturn(completionCursor);
		when(completionCursor.hasNext()).thenReturn(true, false);
		when(completionCursor.next()).thenReturn(new Document("_id", "completion-only").append("count", 1));

		var result = new MongoReportQueryStore(template).findCollectorRankings(
			"task-1", null, null, "latestSubmissionAt", PageRequest.of(0, 20)
		);

		assertEquals(List.of("collector-with-time", "completion-only"),
			result.getContent().stream().map(row -> row.collectorId()).toList());
	}
}

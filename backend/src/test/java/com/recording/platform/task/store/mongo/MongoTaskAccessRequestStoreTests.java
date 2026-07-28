package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoTaskAccessRequestStoreTests {
	@Test
	void pendingSearchJoinsMiniProgramUsersBeforeFacetPaging() {
		SpringDataTaskAccessRequestRepository repository =
			org.mockito.Mockito.mock(SpringDataTaskAccessRequestRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		AggregationResults<Document> result = new AggregationResults<>(
			List.of(new Document("items", List.of()).append("totals", List.of())), new Document()
		);
		when(template.aggregate(
			any(Aggregation.class), eq("task_access_requests"), eq(Document.class)
		)).thenReturn(result);
		MongoTaskAccessRequestStore store = new MongoTaskAccessRequestStore(repository, template);

		store.searchPending("task-1", "张三", PageRequest.of(1, 5));

		ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
		org.mockito.Mockito.verify(template).aggregate(
			aggregation.capture(), eq("task_access_requests"), eq(Document.class)
		);
		String pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
		assertThat(pipeline).contains(
			"taskId", "PENDING", "$lookup", "miniprogram_users",
			"user.name", "user.account", "userId", "$facet", "$skip", "$limit"
		);
	}

	@Test
	void decisionIsAnAtomicPendingOnlyTransition() {
		SpringDataTaskAccessRequestRepository repository =
			org.mockito.Mockito.mock(SpringDataTaskAccessRequestRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		when(template.findAndModify(
			any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(TaskAccessRequest.class)
		)).thenReturn(new TaskAccessRequest());
		MongoTaskAccessRequestStore store = new MongoTaskAccessRequestStore(repository, template);

		store.decideIfPending(
			"request-1",
			AccessRequestStatus.APPROVED,
			"admin-1",
			null,
			Instant.parse("2026-07-11T12:00:00Z")
		);

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
		org.mockito.Mockito.verify(template).findAndModify(
			query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(TaskAccessRequest.class)
		);
		assertThat(query.getValue().getQueryObject())
			.containsEntry("_id", "request-1")
			.containsEntry("status", AccessRequestStatus.PENDING);
		Document set = (Document) update.getValue().getUpdateObject().get("$set");
		assertThat(set)
			.containsEntry("status", AccessRequestStatus.APPROVED)
			.containsEntry("decidedBy", "admin-1");
	}
}

package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.GrantStatus;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

class MongoTaskGrantStoreTests {
	@Test
	void activeSearchJoinsMiniProgramUsersBeforeFacetPaging() {
		SpringDataTaskGrantRepository repository =
			org.mockito.Mockito.mock(SpringDataTaskGrantRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		AggregationResults<Document> result = new AggregationResults<>(
			List.of(new Document("items", List.of()).append("totals", List.of())), new Document()
		);
		when(template.aggregate(
			any(Aggregation.class), eq("task_grants"), eq(Document.class)
		)).thenReturn(result);
		MongoTaskGrantStore store = new MongoTaskGrantStore(repository, template);

		store.search("task-1", GrantStatus.ACTIVE, "MINI-1", PageRequest.of(0, 10));

		ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
		org.mockito.Mockito.verify(template).aggregate(
			aggregation.capture(), eq("task_grants"), eq(Document.class)
		);
		String pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
		assertThat(pipeline).contains(
			"taskId", "ACTIVE", "$lookup", "miniprogram_users",
			"user.name", "user.account", "userId", "$facet", "$skip", "$limit"
		);
	}
}

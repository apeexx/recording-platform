package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskItemCompletionMigrationService;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class TaskItemCompletionMigrationServiceTests {
	@Test
	void backfillsTheEarliestRealCompletionAndReportsUnresolvedLegacyRows() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		when(mongo.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(TaskItem.class)))
			.thenReturn(UpdateResult.acknowledged(2, 2L, (BsonValue) null));
		when(mongo.count(any(Query.class), eq(TaskItem.class))).thenReturn(1L);
		TaskItemCompletionMigrationService service = new TaskItemCompletionMigrationService(mongo);

		var result = service.backfillFirstCompletedAt();

		assertThat(result.migrated()).isEqualTo(2);
		assertThat(result.unresolved()).isEqualTo(1);
		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
		verify(mongo).updateMulti(query.capture(), update.capture(), eq(TaskItem.class));
		assertThat(query.getValue().getQueryObject().toString())
			.contains("firstCompletedAt", "operations", "resultStatus", "COMPLETED");
		assertThat(update.getValue()).isInstanceOf(AggregationUpdate.class);
		assertThat(update.getValue().getUpdateObject().toString())
			.contains(
				"firstCompletedAt", "$min", "$filter", "REVIEW_APPROVE",
				"ADMIN_BATCH_APPROVE", "SUBMIT", "ADMIN_STATUS_CHANGE"
			)
			.doesNotContain("ADMIN_RESTORE", "COLLECTOR_RESTORE");
	}
}

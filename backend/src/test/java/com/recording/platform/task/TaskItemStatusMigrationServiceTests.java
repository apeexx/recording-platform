package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskItemStatusMigrationService;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class TaskItemStatusMigrationServiceTests {
	@Test
	void migratesOnlyLegacyUnassignedReviewPendingItemsAndIsIdempotent() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		when(mongo.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(TaskItem.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, (BsonValue) null))
			.thenReturn(UpdateResult.acknowledged(0, 0L, (BsonValue) null));
		TaskItemStatusMigrationService service = new TaskItemStatusMigrationService(mongo);

		assertThat(service.migrateLegacyUnassignedReviews()).isEqualTo(1);
		assertThat(service.migrateLegacyUnassignedReviews()).isZero();

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo, org.mockito.Mockito.times(2))
			.updateMulti(query.capture(), any(UpdateDefinition.class), eq(TaskItem.class));
		String json = query.getValue().getQueryObject().toString();
		assertThat(json).contains("REVIEW_PENDING", "reviewerId", "reviewAssignmentId");
	}
}

package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.SubmitMutation;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class MongoTaskItemStoreTests {
	@Test
	void claimUsesAnAtomicAvailableQuerySortedByTaskSequence() {
		SpringDataTaskItemRepository repository = org.mockito.Mockito.mock(SpringDataTaskItemRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		TaskItem claimed = new TaskItem();
		when(template.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(TaskItem.class)))
			.thenReturn(claimed);
		MongoTaskItemStore store = new MongoTaskItemStore(repository, template);

		store.claimAvailable(new ClaimMutation(
			"task-1", "collector-1", "张三", "assignment-1", Instant.parse("2026-07-11T12:00:00Z")
		));

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
		org.mockito.Mockito.verify(template).findAndModify(
			query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(TaskItem.class)
		);
		assertThat(query.getValue().getQueryObject())
			.containsEntry("taskId", "task-1")
			.containsEntry("status", TaskItemStatus.AVAILABLE);
		assertThat(query.getValue().getSortObject()).containsEntry("sequence", 1);
		assertThat(update.getValue()).isInstanceOf(AggregationUpdate.class);
		List<Document> stages = ((AggregationUpdate) update.getValue()).getPipeline().getOperations().stream()
			.map((operation) -> operation.toDocument(Aggregation.DEFAULT_CONTEXT))
			.toList();
		assertThat(setStage(stages, "collectorId")).containsEntry("collectorId", "collector-1");
		assertThat(setStage(stages, "assignmentId")).containsEntry("assignmentId", "assignment-1");
		assertThat(setStage(stages, "status")).containsEntry("status", TaskItemStatus.RECORDING_PENDING);
		assertThat(setStage(stages, "revision").get("revision").toString()).contains("$add", "$revision", "1");
		assertThat(setStage(stages, "operations").get("operations").toString())
			.contains("$concatArrays", "resultRevision=$revision", "assignment-1");
	}

	@Test
	void reviewClaimOperationUsesTheAlreadyIncrementedRevision() {
		SpringDataTaskItemRepository repository = org.mockito.Mockito.mock(SpringDataTaskItemRepository.class);
		MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
		MongoTaskItemStore store = new MongoTaskItemStore(repository, mongoTemplate);
		when(mongoTemplate.findAndModify(
			any(Query.class),
			any(UpdateDefinition.class),
			any(FindAndModifyOptions.class),
			eq(TaskItem.class)
		)).thenReturn(new TaskItem());

		store.claimReview(new ReviewClaimMutation(
			"reviewer-1",
			"审核员",
			"review-assignment-1",
			"review-claim-1",
			Instant.parse("2026-07-16T12:00:00Z")
		));

		ArgumentCaptor<UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);
		org.mockito.Mockito.verify(mongoTemplate).findAndModify(
			any(Query.class),
			updateCaptor.capture(),
			any(FindAndModifyOptions.class),
			eq(TaskItem.class)
		);
		AggregationUpdate update = (AggregationUpdate) updateCaptor.getValue();
		List<Document> stages = update.getPipeline().getOperations().stream()
			.map((operation) -> operation.toDocument(Aggregation.DEFAULT_CONTEXT))
			.toList();

		assertThat(setStage(stages, "revision").get("revision").toString()).contains("$add", "$revision", "1");
		assertThat(setStage(stages, "operations").get("operations").toString())
			.contains("resultRevision=$revision")
			.doesNotContain("resultRevision=Document{{$add=[$revision, 1]}}");
	}

	@Test
	void submitConditionsTheAtomicUpdateOnOwnerAssignmentStateAndRevision() {
		SpringDataTaskItemRepository repository = org.mockito.Mockito.mock(SpringDataTaskItemRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(TaskItem.class)))
			.thenReturn(new TaskItem());
		MongoTaskItemStore store = new MongoTaskItemStore(repository, template);

		store.submitIfCurrent(new SubmitMutation(
			"item-1", "collector-1", "张三", "assignment-1", 7, "submit-1",
			new TaskItemResult(null, "文本"), TaskItemStatus.REVIEW_PENDING, Instant.parse("2026-07-11T12:00:00Z")
		));

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		org.mockito.Mockito.verify(template).findAndModify(
			query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(TaskItem.class)
		);
		assertThat(query.getValue().getQueryObject())
			.containsEntry("_id", "item-1")
			.containsEntry("collectorId", "collector-1")
			.containsEntry("assignmentId", "assignment-1")
			.containsEntry("revision", 7L)
			.containsEntry("status", TaskItemStatus.RECORDING_PENDING);
		assertThat(query.getValue().getQueryObject()).containsKey("operations.operationId");
		assertThat((Document) query.getValue().getQueryObject().get("operations.operationId"))
			.containsEntry("$ne", "submit-1");
	}

	@Test
	void collectorReleaseUsesOneSerializableStatusCondition() {
		SpringDataTaskItemRepository repository = org.mockito.Mockito.mock(SpringDataTaskItemRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(TaskItem.class)))
			.thenReturn(new TaskItem());
		MongoTaskItemStore store = new MongoTaskItemStore(repository, template);

		store.releaseIfCurrent(new ReleaseMutation(
			"item-1", "collector-1", "张三", false, 7, "release-1",
			Instant.parse("2026-07-11T12:00:00Z")
		));

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		org.mockito.Mockito.verify(template).findAndModify(
			query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(TaskItem.class)
		);
		assertThat(query.getValue().getQueryObject())
			.containsEntry("_id", "item-1")
			.containsEntry("collectorId", "collector-1")
			.containsEntry("revision", 7L)
			.containsEntry("status", TaskItemStatus.RECORDING_PENDING);
	}

	@Test
	void rejectReplaySnapshotKeepsTheAssignmentAndCurrentResult() {
		SpringDataTaskItemRepository repository = org.mockito.Mockito.mock(SpringDataTaskItemRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		when(template.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(TaskItem.class)))
			.thenReturn(new TaskItem());
		MongoTaskItemStore store = new MongoTaskItemStore(repository, template);
		TaskItemResult currentResult = new TaskItemResult(null, "第一版");

		store.rejectIfCurrent(new RejectMutation(
			"item-1", "reviewer-1", "李审", 2, "reject-1", "噪音过大", "submit-1",
			"assignment-1", currentResult, Instant.parse("2026-07-11T12:00:00Z")
		));

		ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
		org.mockito.Mockito.verify(template).findAndModify(
			any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(TaskItem.class)
		);
		Document push = (Document) update.getValue().getUpdateObject().get("$push");
		assertThat(push.get("operations")).isInstanceOfSatisfying(OperationHistory.class, (operation) -> {
			assertThat(operation.getResultAssignmentId()).isEqualTo("assignment-1");
			assertThat(operation.getResultSnapshot()).isSameAs(currentResult);
			assertThat(operation.getResultStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		});
	}

	private Document setStage(List<Document> stages, String field) {
		return stages.stream()
			.map((stage) -> (Document) stage.get("$set"))
			.filter(java.util.Objects::nonNull)
			.filter((set) -> set.containsKey(field))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing $set stage for " + field));
	}
}

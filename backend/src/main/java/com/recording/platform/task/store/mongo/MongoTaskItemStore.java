package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.SubmitMutation;
import com.recording.platform.task.store.TaskItemStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.stereotype.Repository;
import org.bson.Document;
import java.util.List;

@Repository
public class MongoTaskItemStore implements TaskItemStore {
	private final SpringDataTaskItemRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoTaskItemStore(SpringDataTaskItemRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override public TaskItem save(TaskItem item) { return repository.save(item); }
	@Override public Optional<TaskItem> findById(String id) { return repository.findById(id); }
	@Override public Optional<TaskItem> findByTaskIdAndExternalItemId(String taskId, String externalItemId) {
		return repository.findByTaskIdAndExternalItemId(taskId, externalItemId);
	}
	@Override public Optional<TaskItem> findByTaskIdAndCreationOperationId(String taskId, String operationId) {
		return repository.findByTaskIdAndCreationOperationId(taskId, operationId);
	}
	@Override public Optional<TaskItem> findCurrentByCollector(String collectorId) {
		return repository.findFirstByCollectorIdAndStatus(collectorId, TaskItemStatus.RECORDING_PENDING);
	}

	@Override
	public Optional<TaskItem> claimAvailable(ClaimMutation mutation) {
		Query query = Query.query(Criteria.where("taskId").is(mutation.taskId())
			.and("status").is(TaskItemStatus.AVAILABLE));
		query.with(Sort.by(Sort.Direction.ASC, "sequence"));
		Document operation = new Document()
			.append("operationId", "claim:" + mutation.assignmentId())
			.append("type", "CLAIM")
			.append("actorUserId", mutation.collectorId())
			.append("actorUsername", mutation.actorUsername())
			.append("content", mutation.actorUsername() + "领取了任务条目")
			.append("occurredAt", mutation.occurredAt())
			.append("resultStatus", TaskItemStatus.RECORDING_PENDING)
			.append("resultRevision", "$revision")
			.append("resultAssignmentId", mutation.assignmentId())
			.append("resultSnapshot", null);
		AggregationExpression appendOperation = (context) -> new Document(
			"$concatArrays",
			List.of(
				new Document("$ifNull", List.of("$operations", List.of())),
				List.of(operation)
			)
		);
		UpdateDefinition update = AggregationUpdate.update()
			.set("status").toValue(TaskItemStatus.RECORDING_PENDING)
			.set("collectorId").toValue(mutation.collectorId())
			.set("assignmentId").toValue(mutation.assignmentId())
			.set("updatedAt").toValue(mutation.occurredAt())
			.set("revision").toValue(ArithmeticOperators.Add.valueOf("revision").add(1))
			.set("operations").toValue(appendOperation);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			TaskItem.class
		));
	}

	@Override
	public Optional<TaskItem> submitIfCurrent(SubmitMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.RECORDING_PENDING)
			.and("collectorId").is(mutation.collectorId())
			.and("assignmentId").is(mutation.assignmentId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), mutation.assignmentId(), mutation.expectedRevision() + 1,
			mutation.targetStatus(), mutation.result()
		);
		Update update = new Update()
			.set("currentResult", mutation.result())
			.set("status", mutation.targetStatus())
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("submissions", SubmissionHistory.from(mutation))
			.push("operations", OperationHistory.submission(mutation, snapshot));
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> rejectIfCurrent(RejectMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		if (mutation.reviewedSubmissionOperationId() != null) {
			criteria = criteria.and("submissions.operationId").is(mutation.reviewedSubmissionOperationId());
		}
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), mutation.assignmentId(), mutation.expectedRevision() + 1,
			TaskItemStatus.RECORDING_PENDING, mutation.currentResult()
		);
		Update update = new Update()
			.set("status", TaskItemStatus.RECORDING_PENDING)
			.unset("reviewerId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", OperationHistory.rejection(mutation, snapshot));
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", mutation.reason());
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> releaseIfCurrent(ReleaseMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		if (mutation.admin()) {
			criteria = criteria.and("status").nin(TaskItemStatus.AVAILABLE, TaskItemStatus.DISCARDED);
		} else {
			criteria = criteria.and("status").is(TaskItemStatus.RECORDING_PENDING)
				.and("collectorId").is(mutation.actorUserId());
		}
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.AVAILABLE, null
		);
		Update update = new Update()
			.set("status", TaskItemStatus.AVAILABLE)
			.unset("collectorId")
			.unset("reviewerId")
			.unset("assignmentId")
			.unset("currentResult")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", OperationHistory.release(mutation, snapshot));
		return modify(criteria, update);
	}

	@Override public Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable) {
		return repository.findAllByTaskId(taskId, pageable);
	}

	private Optional<TaskItem> modify(Criteria criteria, Update update) {
		return Optional.ofNullable(mongoTemplate.findAndModify(
			Query.query(criteria),
			update,
			FindAndModifyOptions.options().returnNew(true),
			TaskItem.class
		));
	}

	private TaskItem resultSnapshot(
		String itemId,
		String assignmentId,
		long revision,
		TaskItemStatus status,
		com.recording.platform.task.model.TaskItemResult result
	) {
		TaskItem item = new TaskItem();
		item.setId(itemId);
		item.setAssignmentId(assignmentId);
		item.setRevision(revision);
		item.setStatus(status);
		item.setCurrentResult(result);
		return item;
	}
}

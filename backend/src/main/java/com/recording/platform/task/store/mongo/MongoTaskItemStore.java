package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.task.store.AdminItemTransitionMutation;
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
	public Optional<TaskItem> claimReview(ReviewClaimMutation mutation) {
		Query query = Query.query(Criteria.where("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").exists(false));
		query.with(Sort.by(Sort.Direction.ASC, "updatedAt", "sequence"));
		Document operation = new Document()
			.append("operationId", mutation.operationId())
			.append("type", "REVIEW_CLAIM")
			.append("actorUserId", mutation.reviewerId())
			.append("actorUsername", mutation.actorUsername())
			.append("content", "领取审核任务")
			.append("occurredAt", mutation.occurredAt())
			.append("resultStatus", TaskItemStatus.REVIEW_PENDING)
			.append("resultRevision", new Document("$add", List.of("$revision", 1)))
			.append("resultAssignmentId", "$assignmentId")
			.append("resultSnapshot", "$currentResult");
		AggregationExpression appendOperation = (context) -> new Document("$concatArrays", List.of(
			new Document("$ifNull", List.of("$operations", List.of())), List.of(operation)
		));
		UpdateDefinition update = AggregationUpdate.update()
			.set("reviewerId").toValue(mutation.reviewerId())
			.set("reviewAssignmentId").toValue(mutation.reviewAssignmentId())
			.set("updatedAt").toValue(mutation.occurredAt())
			.set("revision").toValue(ArithmeticOperators.Add.valueOf("revision").add(1))
			.set("operations").toValue(appendOperation);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query, update, FindAndModifyOptions.options().returnNew(true), TaskItem.class
		));
	}

	@Override
	public Optional<TaskItem> releaseReviewIfCurrent(ReviewReleaseMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").is(mutation.reviewerId())
			.and("reviewAssignmentId").is(mutation.reviewAssignmentId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.REVIEW_PENDING, null
		);
		Update update = new Update()
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "REVIEW_RELEASE", mutation.reviewerId(), mutation.actorUsername(),
				"释放审核任务", mutation.occurredAt(), snapshot
			));
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> decideReviewIfCurrent(ReviewDecisionMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").is(mutation.reviewerId())
			.and("reviewAssignmentId").is(mutation.reviewAssignmentId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		if (mutation.reviewedSubmissionOperationId() != null) {
			criteria = criteria.and("submissions.operationId").is(mutation.reviewedSubmissionOperationId());
		}
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, mutation.targetStatus(), mutation.result()
		);
		String type = mutation.targetStatus() == TaskItemStatus.COMPLETED ? "REVIEW_APPROVE" : "REVIEW_REJECT";
		String content = mutation.targetStatus() == TaskItemStatus.COMPLETED
			? "审核环节提交" : "审核环节驳回到采集环节：" + mutation.conclusion();
		Update update = new Update()
			.set("status", mutation.targetStatus())
			.set("currentResult", mutation.result())
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), type, mutation.reviewerId(), mutation.actorUsername(), content,
				mutation.occurredAt(), snapshot
			));
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", mutation.conclusion());
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> assignReviewIfCurrent(ReviewAssignMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").exists(false)
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.REVIEW_PENDING, null
		);
		Update update = new Update()
			.set("reviewerId", mutation.reviewerId())
			.set("reviewAssignmentId", mutation.reviewAssignmentId())
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "REVIEW_ASSIGN", mutation.actorUserId(), mutation.actorUsername(),
				"将审核任务分配给" + mutation.reviewerName(), mutation.occurredAt(), snapshot
			));
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminApproveReviewIfCurrent(AdminReviewApproveMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		if (mutation.reviewedSubmissionOperationId() != null) {
			criteria = criteria.and("submissions.operationId").is(mutation.reviewedSubmissionOperationId());
		}
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.COMPLETED, mutation.result()
		);
		Update update = new Update()
			.set("status", TaskItemStatus.COMPLETED)
			.set("currentResult", mutation.result())
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "ADMIN_BATCH_APPROVE", mutation.actorUserId(), mutation.actorUsername(),
				"管理员批量通过审核", mutation.occurredAt(), snapshot
			));
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", "管理员批量通过");
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminTransitionIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation).and("status").ne(TaskItemStatus.DISCARDED);
		Update update = adminUpdate(mutation, "ADMIN_STATUS_CHANGE", "将该任务调整到" + mutation.targetStatus());
		update.set("status", mutation.targetStatus());
		applyOwnership(update, mutation);
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminDiscardIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation).and("status").ne(TaskItemStatus.DISCARDED);
		Update update = adminUpdate(mutation, "ADMIN_DISCARD", "将该任务调整到废弃数据")
			.set("discardedPreviousStatus", mutation.sourceStatus())
			.set("status", TaskItemStatus.DISCARDED);
		return modify(criteria, update);
	}

	private Criteria adminCriteria(AdminItemTransitionMutation mutation) {
		return Criteria.where("_id").is(mutation.itemId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
	}

	private Update adminUpdate(AdminItemTransitionMutation mutation, String type, String content) {
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), mutation.assignmentId(), mutation.expectedRevision() + 1,
			mutation.targetStatus(), null
		);
		return new Update()
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), type, mutation.actorUserId(), mutation.actorUsername(), content,
				mutation.occurredAt(), snapshot
			));
	}

	private void applyOwnership(Update update, AdminItemTransitionMutation mutation) {
		if (mutation.collectorId() == null) update.unset("collectorId");
		else update.set("collectorId", mutation.collectorId());
		if (mutation.assignmentId() == null) update.unset("assignmentId");
		else update.set("assignmentId", mutation.assignmentId());
		update.unset("reviewerId").unset("reviewAssignmentId");
	}

	@Override
	public Optional<TaskItem> adminRestoreIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation)
			.and("status").is(TaskItemStatus.DISCARDED)
			.and("discardedPreviousStatus").is(mutation.targetStatus());
		Update update = adminUpdate(mutation, "ADMIN_RESTORE", "恢复废弃数据到" + mutation.targetStatus())
			.set("status", mutation.targetStatus())
			.unset("discardedPreviousStatus");
		applyOwnership(update, mutation);
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

	@Override public List<TaskItem> findForReport(String collectorId, String taskId) {
		Criteria criteria = new Criteria();
		List<Criteria> filters = new java.util.ArrayList<>();
		if (collectorId != null) filters.add(Criteria.where("collectorId").is(collectorId));
		if (taskId != null) filters.add(Criteria.where("taskId").is(taskId));
		Query query = filters.isEmpty() ? new Query()
			: Query.query(criteria.andOperator(filters.toArray(Criteria[]::new)));
		return mongoTemplate.find(query, TaskItem.class);
	}

	@Override public Page<TaskItem> findReviewPool(Pageable pageable) {
		return repository.findAllByStatusAndReviewerIdIsNull(TaskItemStatus.REVIEW_PENDING, pageable);
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

	private OperationHistory reviewOperation(
		String operationId,
		String type,
		String actorUserId,
		String actorUsername,
		String content,
		java.time.Instant occurredAt,
		TaskItem item
	) {
		OperationHistory operation = new OperationHistory();
		operation.setOperationId(operationId);
		operation.setType(type);
		operation.setActorUserId(actorUserId);
		operation.setActorUsername(actorUsername);
		operation.setContent(content);
		operation.setOccurredAt(occurredAt);
		operation.setResultStatus(item.getStatus());
		operation.setResultRevision(item.getRevision());
		operation.setResultAssignmentId(item.getAssignmentId());
		operation.setResultSnapshot(item.getCurrentResult());
		return operation;
	}
}

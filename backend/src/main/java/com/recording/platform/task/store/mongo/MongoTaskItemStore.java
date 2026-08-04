package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.SubmissionHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewItemClaimMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.ReviewDiscardMutation;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.task.store.AdminReviewDecisionMutation;
import com.recording.platform.task.store.AdminItemTransitionMutation;
import com.recording.platform.task.store.SubmitMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.ReviewTaskMetrics;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import com.recording.platform.task.store.UpdateTaskItemReferencesMutation;
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
	@Override public Optional<TaskItem> findByTaskIdAndCreationOperationId(String taskId, String operationId) {
		return repository.findByTaskIdAndCreationOperationId(taskId, operationId);
	}
	@Override
	public Optional<TaskItem> findByTaskIdAndSourcePlatformAndSourceItemId(
		String taskId, String sourcePlatform, String sourceItemId
	) {
		return repository.findByTaskIdAndSourcePlatformAndSourceItemId(
			taskId, sourcePlatform, sourceItemId
		);
	}
	@Override
	public Optional<TaskItem> updateReferencesIfAvailable(UpdateTaskItemReferencesMutation mutation) {
		TaskItem snapshot = new TaskItem();
		snapshot.setId(mutation.itemId());
		snapshot.setStatus(TaskItemStatus.AVAILABLE);
		snapshot.setRevision(mutation.expectedRevision() + 1);
		Update update = new Update()
			.set("referenceText", mutation.referenceText())
			.set("referenceAudioUrl", mutation.referenceAudioUrl())
			.set("referenceVideoUrl", mutation.referenceVideoUrl())
			.set("referenceAudioMediaId", mutation.referenceAudioMediaId())
			.set("referenceVideoMediaId", mutation.referenceVideoMediaId())
			.unset("referenceAudioDurationMillis")
			.unset("referenceVideoDurationMillis")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", OperationHistory.referenceEdit(
				mutation.operationId(), mutation.actorUserId(), mutation.actorUsername(),
				mutation.occurredAt(), snapshot
			));
		return modify(
			Criteria.where("_id").is(mutation.itemId())
				.and("status").is(TaskItemStatus.AVAILABLE)
				.and("revision").is(mutation.expectedRevision()),
			update
		);
	}

	@Override
	public Optional<TaskItem> deleteAvailableIfCurrent(String itemId, long expectedRevision) {
		return Optional.ofNullable(mongoTemplate.findAndRemove(
			Query.query(Criteria.where("_id").is(itemId)
				.and("status").is(TaskItemStatus.AVAILABLE)
				.and("revision").is(expectedRevision)),
			TaskItem.class
		));
	}
	@Override
	public Optional<TaskItem> claimAvailable(ClaimMutation mutation) {
		Query query = Query.query(Criteria.where("taskId").is(mutation.taskId())
			.and("status").is(TaskItemStatus.AVAILABLE)
			.and("operations").not().elemMatch(
				Criteria.where("type").is("RELEASE")
					.and("actorUserId").is(mutation.collectorId())
					.and("occurredAt").gt(mutation.releaseCooldownSince())
			));
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
			.and("status").in(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING, TaskItemStatus.SUBMITTED)
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
			.set("firstSubmittedAt", mutation.firstSubmittedAt())
			.set("latestSubmittedAt", mutation.occurredAt())
			.set("referenceAudioDurationMillis", mutation.referenceAudioDurationMillis())
			.set("referenceVideoDurationMillis", mutation.referenceVideoDurationMillis())
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("submissions", SubmissionHistory.from(mutation))
			.push("operations", OperationHistory.submission(mutation, snapshot));
		if (mutation.targetStatus() == TaskItemStatus.COMPLETED) {
			update.set("firstCompletedAt", mutation.firstCompletedAt());
		}
		update.unset("currentRejection").unset("reviewFinalAnswer");
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> claimReview(ReviewClaimMutation mutation) {
		Query query = Query.query(Criteria.where("taskId").is(mutation.taskId())
			.and("status").is(TaskItemStatus.SUBMITTED)
			.and("reviewerId").is(null));
		query.with(Sort.by(Sort.Direction.ASC, "updatedAt", "sequence"));
		Document operation = new Document()
			.append("operationId", mutation.operationId())
			.append("type", "REVIEW_CLAIM")
			.append("actorUserId", mutation.reviewerId())
			.append("actorUsername", mutation.actorUsername())
			.append("content", "领取审核任务")
			.append("occurredAt", mutation.occurredAt())
			.append("resultStatus", TaskItemStatus.REVIEW_PENDING)
			.append("resultRevision", "$revision")
			.append("resultAssignmentId", "$assignmentId")
			.append("resultSnapshot", "$currentResult");
		AggregationExpression appendOperation = (context) -> new Document("$concatArrays", List.of(
			new Document("$ifNull", List.of("$operations", List.of())), List.of(operation)
		));
		UpdateDefinition update = AggregationUpdate.update()
			.set("status").toValue(TaskItemStatus.REVIEW_PENDING)
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
	public Optional<TaskItem> claimReviewItem(ReviewItemClaimMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.SUBMITTED)
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), mutation.collectorAssignmentId(), mutation.expectedRevision() + 1,
			TaskItemStatus.REVIEW_PENDING, mutation.currentResult()
		);
		Update update = new Update()
			.set("status", TaskItemStatus.REVIEW_PENDING)
			.set("reviewerId", mutation.reviewerId())
			.set("reviewAssignmentId", mutation.reviewAssignmentId())
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "REVIEW_CLAIM", mutation.reviewerId(), mutation.actorUsername(),
				"领取审核任务", mutation.occurredAt(), snapshot
			));
		return modify(criteria, update);
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
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.SUBMITTED, null
		);
		Update update = new Update()
			.set("status", TaskItemStatus.SUBMITTED)
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
	public Optional<TaskItem> discardReviewIfCurrent(ReviewDiscardMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").is(mutation.reviewerId())
			.and("reviewAssignmentId").is(mutation.reviewAssignmentId())
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), mutation.assignmentId(), mutation.expectedRevision() + 1,
			TaskItemStatus.DISCARDED, null
		);
		Update update = new Update()
			.set("status", TaskItemStatus.DISCARDED)
			.set("discardedPreviousStatus", TaskItemStatus.REVIEW_PENDING)
			.set("currentDiscard", new com.recording.platform.task.model.CurrentDiscard(
				mutation.reason(), mutation.actorUserId(), mutation.actorUsername(),
				mutation.actorRole(), mutation.occurredAt()
			))
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "REVIEW_DISCARD", mutation.actorUserId(),
				mutation.actorUsername(), "审核员将该任务标记为无效数据：" + mutation.reason(),
				mutation.occurredAt(), snapshot
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
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), type, mutation.reviewerId(), mutation.actorUsername(), content,
				mutation.occurredAt(), snapshot
			));
		if (mutation.targetStatus() == TaskItemStatus.COMPLETED) {
			update.set("firstCompletedAt", mutation.firstCompletedAt());
		}
		applyReviewFinalAnswer(update, mutation.targetStatus(), mutation.reviewFinalAnswer());
		if (mutation.targetStatus() == TaskItemStatus.REWORK_PENDING) {
			update.set("currentRejection", mutation.currentRejection());
		} else {
			update.unset("currentRejection");
		}
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", mutation.conclusion());
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> assignReviewIfCurrent(ReviewAssignMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.SUBMITTED)
			.and("reviewerId").is(null)
			.and("revision").is(mutation.expectedRevision())
			.and("operations.operationId").ne(mutation.operationId());
		TaskItem snapshot = resultSnapshot(
			mutation.itemId(), null, mutation.expectedRevision() + 1, TaskItemStatus.REVIEW_PENDING, null
		);
		Update update = new Update()
			.set("status", TaskItemStatus.REVIEW_PENDING)
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
			.and("reviewerId").ne(null)
			.and("reviewAssignmentId").ne(null)
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
			.set("firstCompletedAt", mutation.firstCompletedAt())
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), "ADMIN_BATCH_APPROVE", mutation.actorUserId(), mutation.actorUsername(),
				"管理员批量通过审核", mutation.occurredAt(), snapshot
			));
		applyReviewFinalAnswer(update, TaskItemStatus.COMPLETED, mutation.reviewFinalAnswer());
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", "管理员批量通过");
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminDecideReviewIfCurrent(AdminReviewDecisionMutation mutation) {
		Criteria criteria = Criteria.where("_id").is(mutation.itemId())
			.and("status").is(TaskItemStatus.REVIEW_PENDING)
			.and("reviewerId").ne(null)
			.and("reviewAssignmentId").ne(null)
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
			.unset("reviewerId")
			.unset("reviewAssignmentId")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", reviewOperation(
				mutation.operationId(), type, mutation.actorUserId(), mutation.actorUsername(), content,
				mutation.occurredAt(), snapshot
			));
		applyReviewFinalAnswer(update, mutation.targetStatus(), mutation.reviewFinalAnswer());
		if (mutation.targetStatus() == TaskItemStatus.COMPLETED) {
			update.set("firstCompletedAt", mutation.firstCompletedAt());
		}
		if (mutation.targetStatus() == TaskItemStatus.REWORK_PENDING) {
			update.set("currentRejection", mutation.currentRejection());
		} else {
			update.unset("currentRejection");
		}
		if (mutation.reviewedSubmissionOperationId() != null) {
			update.set("submissions.$.reviewConclusion", mutation.conclusion());
		}
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminTransitionIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation).and("status").ne(TaskItemStatus.DISCARDED);
		Update update = adminUpdate(mutation, "ADMIN_STATUS_CHANGE", "将该任务调整到" + mutation.targetStatus());
		update.set("status", mutation.targetStatus());
		if (mutation.targetStatus() == TaskItemStatus.COMPLETED) {
			update.set("firstCompletedAt", mutation.firstCompletedAt());
		}
		applyOwnership(update, mutation);
		return modify(criteria, update);
	}

	@Override
	public Optional<TaskItem> adminDiscardIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation).and("status").ne(TaskItemStatus.DISCARDED);
		String type = mutation.actorRole() == com.recording.platform.identity.model.UserRole.COLLECTOR
			? "COLLECTOR_DISCARD" : "ADMIN_DISCARD";
		Update update = adminUpdate(mutation, type, "将该任务调整到废弃数据：" + mutation.reason())
			.set("discardedPreviousStatus", mutation.sourceStatus())
			.set("currentDiscard", new com.recording.platform.task.model.CurrentDiscard(
				mutation.reason(), mutation.actorUserId(), mutation.actorUsername(),
				mutation.actorRole(), mutation.occurredAt()
			))
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

	private void applyReviewFinalAnswer(Update update, TaskItemStatus targetStatus, String reviewFinalAnswer) {
		if (targetStatus == TaskItemStatus.COMPLETED && reviewFinalAnswer != null) {
			update.set("reviewFinalAnswer", reviewFinalAnswer);
		} else {
			update.unset("reviewFinalAnswer");
		}
	}

	@Override
	public Optional<TaskItem> adminRestoreIfCurrent(AdminItemTransitionMutation mutation) {
		Criteria criteria = adminCriteria(mutation)
			.and("status").is(TaskItemStatus.DISCARDED)
			.and("discardedPreviousStatus").is(mutation.targetStatus());
		String type = mutation.actorRole() == com.recording.platform.identity.model.UserRole.COLLECTOR
			? "COLLECTOR_RESTORE" : "ADMIN_RESTORE";
		Update update = adminUpdate(mutation, type, "恢复废弃数据到" + mutation.targetStatus())
			.set("status", mutation.targetStatus())
			.unset("discardedPreviousStatus")
			.unset("currentDiscard");
		if (mutation.targetStatus() != TaskItemStatus.REVIEW_PENDING) {
			applyOwnership(update, mutation);
		}
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
			TaskItemStatus.REWORK_PENDING, mutation.currentResult()
		);
		Update update = new Update()
			.set("status", TaskItemStatus.REWORK_PENDING)
			.set("currentRejection", new com.recording.platform.task.model.CurrentRejection(
				List.of(mutation.reason()), null, mutation.occurredAt(), mutation.actorUserId(), mutation.actorUsername()
			))
			.unset("reviewerId")
			.unset("reviewAssignmentId")
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
			criteria = criteria.and("status").in(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING)
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
			.unset("reviewFinalAnswer")
			.unset("firstSubmittedAt")
			.unset("latestSubmittedAt")
			.unset("firstCompletedAt")
			.set("updatedAt", mutation.occurredAt())
			.inc("revision", 1L)
			.push("operations", OperationHistory.release(mutation, snapshot));
		return modify(criteria, update);
	}

	@Override public Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable) {
		return repository.findAllByTaskId(taskId, pageable);
	}

	@Override
	public Page<TaskItem> findAllByTaskId(
		String taskId, com.recording.platform.task.service.TaskItemFilter filter, Pageable pageable
	) {
		com.recording.platform.task.service.TaskItemFilter normalized =
			filter == null ? com.recording.platform.task.service.TaskItemFilter.all() : filter;
		List<Criteria> filters = new java.util.ArrayList<>();
		filters.add(Criteria.where("taskId").is(taskId));
		if (!normalized.itemCodes().isEmpty()) {
			filters.add(Criteria.where("itemCode").in(normalized.itemCodes()));
		}
		if (!normalized.itemCodeQuery().isBlank()) {
			filters.add(Criteria.where("itemCode").regex(
				java.util.regex.Pattern.quote(normalized.itemCodeQuery()), "i"
			));
		}
		if (!normalized.sourceItemIdQuery().isBlank()) {
			filters.add(Criteria.where("sourceItemId").regex(
				"^" + java.util.regex.Pattern.quote(normalized.sourceItemIdQuery())
			));
		}
		if (normalized.firstSubmittedFrom() != null || normalized.firstSubmittedTo() != null) {
			Criteria submitted = Criteria.where("firstSubmittedAt");
			Criteria completed = Criteria.where("firstCompletedAt");
			if (normalized.firstSubmittedFrom() != null) {
				submitted = submitted.gte(normalized.firstSubmittedFrom());
				completed = completed.gte(normalized.firstSubmittedFrom());
			}
			if (normalized.firstSubmittedTo() != null) {
				submitted = submitted.lt(normalized.firstSubmittedTo());
				completed = completed.lt(normalized.firstSubmittedTo());
			}
			filters.add(new Criteria().orOperator(submitted, completed));
		}
		if (!normalized.groups().isEmpty()) {
			filters.add(Criteria.where("status").in(normalized.groups().stream()
				.flatMap(group -> group.statuses().stream())
				.collect(java.util.stream.Collectors.toSet())));
		}
		if (!normalized.collectorIds().isEmpty() || normalized.includeUnassigned()) {
			List<Criteria> collectors = new java.util.ArrayList<>();
			if (!normalized.collectorIds().isEmpty()) {
				collectors.add(Criteria.where("collectorId").in(normalized.collectorIds()));
			}
			if (normalized.includeUnassigned()) {
				collectors.add(new Criteria().orOperator(
					Criteria.where("collectorId").exists(false), Criteria.where("collectorId").is(null)
				));
			}
			filters.add(collectors.size() == 1 ? collectors.get(0)
				: new Criteria().orOperator(collectors.toArray(Criteria[]::new)));
		}
		if (!normalized.results().isEmpty()) {
			Criteria[] resultCriteria = normalized.results().stream()
				.map(this::resultCriteria).toArray(Criteria[]::new);
			filters.add(resultCriteria.length == 1
				? resultCriteria[0] : new Criteria().orOperator(resultCriteria));
		}
		Criteria criteria = new Criteria().andOperator(filters.toArray(Criteria[]::new));
		Query query = Query.query(criteria).with(pageable);
		long total = mongoTemplate.count(Query.query(criteria), TaskItem.class);
		return new org.springframework.data.domain.PageImpl<>(
			mongoTemplate.find(query, TaskItem.class), pageable, total
		);
	}

	private Criteria resultCriteria(com.recording.platform.task.service.TaskItemResultKind result) {
		Criteria hasText = Criteria.where("currentResult.text").regex(".*\\S.*");
		Criteria noText = new Criteria().orOperator(
			Criteria.where("currentResult.text").exists(false),
			Criteria.where("currentResult.text").is(null),
			Criteria.where("currentResult.text").regex("^\\s*$")
		);
		Criteria hasAudio = Criteria.where("currentResult.audio").ne(null);
		Criteria noAudio = new Criteria().orOperator(
			Criteria.where("currentResult.audio").exists(false),
			Criteria.where("currentResult.audio").is(null)
		);
		return switch (result) {
			case NONE -> new Criteria().andOperator(noText, noAudio);
			case TEXT_ONLY -> new Criteria().andOperator(hasText, noAudio);
			case AUDIO_ONLY -> new Criteria().andOperator(noText, hasAudio);
			case TEXT_AND_AUDIO -> new Criteria().andOperator(hasText, hasAudio);
			case ALL -> new Criteria();
		};
	}

	@Override public void deleteAllByTaskId(String taskId) {
		repository.deleteAllByTaskId(taskId);
	}

	@Override public Page<TaskItem> findAllByCollectorIdAndStatusIn(
		String collectorId, String taskId, java.util.Collection<TaskItemStatus> statuses, Pageable pageable
	) {
		Criteria criteria = Criteria.where("collectorId").is(collectorId).and("status").in(statuses);
		if (taskId != null && !taskId.isBlank()) criteria = criteria.and("taskId").is(taskId);
		long total = mongoTemplate.count(Query.query(criteria), TaskItem.class);
		List<TaskItem> content = mongoTemplate.find(Query.query(criteria).with(pageable), TaskItem.class);
		return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
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
		return repository.findAllByStatusAndReviewerIdIsNull(TaskItemStatus.SUBMITTED, pageable);
	}

	@Override public Page<TaskItem> findAllReviewPending(Pageable pageable) {
		Criteria criteria = Criteria.where("status").in(TaskItemStatus.SUBMITTED, TaskItemStatus.REVIEW_PENDING);
		Query query = Query.query(criteria).with(pageable);
		long total = mongoTemplate.count(Query.query(criteria), TaskItem.class);
		return new org.springframework.data.domain.PageImpl<>(mongoTemplate.find(query, TaskItem.class), pageable, total);
	}

	@Override public long countReviewPendingByTaskId(String taskId) {
		return mongoTemplate.count(Query.query(Criteria.where("taskId").is(taskId)
			.and("status").in(TaskItemStatus.SUBMITTED, TaskItemStatus.REVIEW_PENDING)), TaskItem.class);
	}

	@Override
	public List<ReviewTaskMetrics> reviewTaskMetrics(
		Collection<String> taskIds, Instant todayStart, Instant tomorrowStart
	) {
		if (taskIds == null || taskIds.isEmpty()) return List.of();
		Document entered = new Document("$in", List.of("$status", List.of(
			TaskItemStatus.SUBMITTED.name(),
			TaskItemStatus.REVIEW_PENDING.name(),
			TaskItemStatus.REWORK_PENDING.name(),
			TaskItemStatus.COMPLETED.name()
		)));
		Document group = new Document("_id", "$taskId")
			.append("effectiveItemCount", conditionalSum(new Document("$ne", List.of(
				"$status", TaskItemStatus.DISCARDED.name()
			))))
			.append("completedCount", conditionalSum(new Document("$eq", List.of(
				"$status", TaskItemStatus.COMPLETED.name()
			))))
			.append("reviewEnteredCount", conditionalSum(entered))
			.append("reviewProcessedCount", conditionalSum(new Document("$in", List.of(
				"$status", List.of(TaskItemStatus.COMPLETED.name(), TaskItemStatus.REWORK_PENDING.name())
			))))
			.append("submittedCount", conditionalSum(new Document("$eq", List.of(
				"$status", TaskItemStatus.SUBMITTED.name()
			))))
			.append("reviewPendingCount", conditionalSum(new Document("$eq", List.of(
				"$status", TaskItemStatus.REVIEW_PENDING.name()
			))))
			.append("todayCompletedCount", conditionalSum(new Document("$and", List.of(
				new Document("$eq", List.of("$status", TaskItemStatus.COMPLETED.name())),
				new Document("$gte", List.of("$firstCompletedAt", Date.from(todayStart))),
				new Document("$lt", List.of("$firstCompletedAt", Date.from(tomorrowStart)))
			))));
		List<Document> pipeline = List.of(
			new Document("$match", new Document("taskId", new Document("$in", taskIds))),
			new Document("$group", group)
		);
		List<ReviewTaskMetrics> result = new java.util.ArrayList<>();
		for (Document row : mongoTemplate.getCollection("task_items").aggregate(pipeline)) {
			result.add(new ReviewTaskMetrics(
				row.getString("_id"),
				number(row, "effectiveItemCount"), number(row, "completedCount"),
				number(row, "reviewEnteredCount"), number(row, "reviewProcessedCount"),
				number(row, "submittedCount"), number(row, "reviewPendingCount"),
				number(row, "todayCompletedCount")
			));
		}
		return result;
	}

	private Document conditionalSum(Document condition) {
		return new Document("$sum", new Document("$cond", List.of(condition, 1, 0)));
	}

	private long number(Document row, String key) {
		Number value = row.get(key, Number.class);
		return value == null ? 0 : value.longValue();
	}

	@Override public Page<TaskItem> findReviewPoolByTaskId(
		String taskId, boolean includeAssigned, String reviewerId, Pageable pageable
	) {
		return findReviewPoolByTaskId(
			taskId, includeAssigned, reviewerId,
			com.recording.platform.review.service.ReviewPoolFilter.all(), pageable
		);
	}

	@Override public Page<TaskItem> findReviewPoolByTaskId(
		String taskId,
		boolean includeAssigned,
		String reviewerId,
		com.recording.platform.review.service.ReviewPoolFilter filter,
		Pageable pageable
	) {
		var normalized = filter == null
			? com.recording.platform.review.service.ReviewPoolFilter.all() : filter;
		List<Criteria> filters = new java.util.ArrayList<>();
		filters.add(Criteria.where("taskId").is(taskId));
		Criteria visibility = includeAssigned
			? Criteria.where("status").in(TaskItemStatus.SUBMITTED, TaskItemStatus.REVIEW_PENDING)
			: new Criteria().orOperator(
				new Criteria().andOperator(
					Criteria.where("status").is(TaskItemStatus.SUBMITTED),
					new Criteria().orOperator(
						Criteria.where("reviewerId").exists(false), Criteria.where("reviewerId").is(null)
					)
				),
				new Criteria().andOperator(
					Criteria.where("status").is(TaskItemStatus.REVIEW_PENDING),
					Criteria.where("reviewerId").is(reviewerId)
				)
			);
		filters.add(visibility);
		if (!normalized.itemCodes().isEmpty()) {
			filters.add(Criteria.where("itemCode").in(normalized.itemCodes()));
		}
		if (!normalized.itemCodeQuery().isBlank()) {
			filters.add(Criteria.where("itemCode").regex(
				java.util.regex.Pattern.quote(normalized.itemCodeQuery()), "i"
			));
		}
		if (!normalized.statuses().isEmpty()) {
			filters.add(Criteria.where("status").in(normalized.statuses()));
		}
		if (!normalized.collectorIds().isEmpty()) {
			filters.add(Criteria.where("collectorId").in(normalized.collectorIds()));
		}
		if (!normalized.reviewerIds().isEmpty() || normalized.includeUnassignedReviewer()) {
			List<Criteria> reviewers = new java.util.ArrayList<>();
			if (!normalized.reviewerIds().isEmpty()) {
				reviewers.add(Criteria.where("reviewerId").in(normalized.reviewerIds()));
			}
			if (normalized.includeUnassignedReviewer()) {
				reviewers.add(new Criteria().orOperator(
					Criteria.where("reviewerId").exists(false), Criteria.where("reviewerId").is(null)
				));
			}
			filters.add(reviewers.size() == 1 ? reviewers.get(0)
				: new Criteria().orOperator(reviewers.toArray(Criteria[]::new)));
		}
		if (!normalized.results().isEmpty()) {
			Criteria[] results = normalized.results().stream()
				.map(this::resultCriteria).toArray(Criteria[]::new);
			filters.add(results.length == 1 ? results[0] : new Criteria().orOperator(results));
		}
		Criteria criteria = new Criteria().andOperator(filters.toArray(Criteria[]::new));
		Query query = Query.query(criteria).with(pageable);
		List<TaskItem> content = mongoTemplate.find(query, TaskItem.class);
		long total = mongoTemplate.count(Query.query(criteria), TaskItem.class);
		return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
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

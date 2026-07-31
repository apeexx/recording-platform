package com.recording.platform.review.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.task.model.CurrentRejection;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewItemClaimMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.task.store.AdminReviewDecisionMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.ReviewTaskMetrics;
import com.recording.platform.time.BusinessDayPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReviewService {
	private final TaskItemStore items;
	private final Clock clock;
	private final IdentityDirectory users;
	private final TaskStore tasks;

	public ReviewService(TaskItemStore items, TaskStore tasks, Clock clock) {
		this(items, null, tasks, clock);
	}

	@Autowired
	public ReviewService(TaskItemStore items, IdentityDirectory users, TaskStore tasks, Clock clock) {
		this.items = items;
		this.users = users;
		this.tasks = tasks;
		this.clock = clock;
	}

	public TaskItem claim(String operationId, PlatformPrincipal actor) {
		throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_ID_REQUIRED", "请先选择审核任务");
	}

	public TaskItem claim(String taskId, String operationId, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		return claimOptional(taskId, requiredOperationId(operationId), actor).orElseThrow(() ->
			new ApiException(HttpStatus.NOT_FOUND, "NO_REVIEW_ITEM", "当前没有可领取的待审核数据")
		);
	}

	public TaskItem claimItem(
		String itemId, String operationId, long expectedRevision, PlatformPrincipal actor
	) {
		requireReviewAccess(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.SUBMITTED || item.getRevision() != expectedRevision) throw stale();
		ReviewItemClaimMutation mutation = new ReviewItemClaimMutation(
			itemId, actor.userId(), actorName(actor), UUID.randomUUID().toString(),
			item.getAssignmentId(), item.getCurrentResult(), expectedRevision,
			requiredOperationId(operationId), Instant.now(clock)
		);
		return items.claimReviewItem(mutation).orElseThrow(this::stale);
	}

	public List<ReviewTaskSummary> tasks(PlatformPrincipal actor) {
		return tasks(actor, false);
	}

	public List<ReviewTaskSummary> tasks(PlatformPrincipal actor, boolean includeCleared) {
		requireReviewAccess(actor);
		if (tasks == null) return List.of();
		List<TaskRecord> allTasks = tasks.findAll(Pageable.unpaged()).getContent();
		Map<String, ReviewTaskMetrics> metrics = metricsByTask(
			allTasks.stream().map(TaskRecord::getId).toList()
		);
		return allTasks.stream()
			.filter(task -> !includeCleared || task.getConfiguration() != null
				&& task.getConfiguration().isHumanReviewEnabled())
			.map(task -> summary(task, metrics.get(task.getId())))
			.filter(summary -> includeCleared || summary.pendingCount() > 0)
			.sorted(Comparator.comparingLong(ReviewTaskSummary::pendingCount).reversed()
				.thenComparing(ReviewTaskSummary::taskCode))
			.toList();
	}

	public ReviewTaskSummary taskSummary(String taskId, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		if (tasks == null) throw taskNotFound();
		TaskRecord task = tasks.findById(taskId).orElseThrow(this::taskNotFound);
		return summary(task, metricsByTask(List.of(taskId)).get(taskId));
	}

	private Map<String, ReviewTaskMetrics> metricsByTask(List<String> taskIds) {
		if (taskIds.isEmpty()) return Map.of();
		var today = BusinessDayPolicy.currentDate(clock);
		Instant todayStart = BusinessDayPolicy.start(today);
		Instant tomorrowStart = BusinessDayPolicy.endExclusive(today);
		return items.reviewTaskMetrics(taskIds, todayStart, tomorrowStart).stream()
			.collect(Collectors.toMap(ReviewTaskMetrics::taskId, Function.identity()));
	}

	private ReviewTaskSummary summary(TaskRecord task, ReviewTaskMetrics metrics) {
		ReviewTaskMetrics value = metrics == null
			? new ReviewTaskMetrics(task.getId(), 0, 0, 0, 0, 0, 0, 0)
			: metrics;
		return new ReviewTaskSummary(
			task.getId(), task.getTaskCode(), task.getName(), value.pendingCount(),
			value.effectiveItemCount(), value.completedCount(), value.reviewEnteredCount(),
			value.reviewProcessedCount(), value.submittedCount(), value.reviewPendingCount(),
			value.todayCompletedCount()
		);
	}

	private ApiException taskNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
	}

	public Page<ReviewPoolItemView> pool(
		String taskId, ReviewPoolFilter filter, Pageable pageable, PlatformPrincipal actor
	) {
		requireReviewAccess(actor);
		Page<TaskItem> pool = items.findReviewPoolByTaskId(
			taskId,
			actor.role() == UserRole.ADMIN,
			actor.role() == UserRole.REVIEWER ? actor.userId() : null,
			filter == null ? ReviewPoolFilter.all() : filter,
			pageable
		);
		Map<String, IdentityUser> identities = users == null ? Map.of() : users.findAllByIdIn(
			pool.getContent().stream()
				.flatMap(item -> java.util.stream.Stream.of(item.getCollectorId(), item.getReviewerId()))
				.filter(java.util.Objects::nonNull).distinct().toList()
		).stream().collect(Collectors.toMap(IdentityUser::id, Function.identity()));
		return pool.map(item -> ReviewPoolItemView.from(
			item, identities.get(item.getCollectorId()), identities.get(item.getReviewerId())
		));
	}

	public Page<ReviewPoolItemView> pool(String taskId, Pageable pageable, PlatformPrincipal actor) {
		return pool(taskId, ReviewPoolFilter.all(), pageable, actor);
	}

	public List<ReviewFilterUserView> filterUsers(
		String taskId, UserRole role, String query, PlatformPrincipal actor
	) {
		requireReviewAccess(actor);
		if (role != UserRole.COLLECTOR && role != UserRole.REVIEWER) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REVIEW_FILTER_ROLE",
				"审核筛选人员角色只能是采集员或审核员"
			);
		}
		if (users == null) return List.of();
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
		Map<String, ReviewFilterUserView> matches = new LinkedHashMap<>();
		int pageNumber = 0;
		Page<TaskItem> page;
		do {
			page = items.findReviewPoolByTaskId(
				taskId,
				actor.role() == UserRole.ADMIN,
				actor.role() == UserRole.REVIEWER ? actor.userId() : null,
				ReviewPoolFilter.all(),
				PageRequest.of(pageNumber++, 200)
			);
			List<String> ids = page.getContent().stream()
				.map(item -> role == UserRole.COLLECTOR ? item.getCollectorId() : item.getReviewerId())
				.filter(java.util.Objects::nonNull)
				.filter(id -> !matches.containsKey(id))
				.distinct()
				.toList();
			users.findAllByIdIn(ids).stream()
				.filter(user -> user.role() == role)
				.filter(user -> matchesQuery(user, normalizedQuery))
				.map(ReviewFilterUserView::from)
				.forEach(user -> matches.putIfAbsent(user.id(), user));
		} while (page.hasNext());
		return matches.values().stream()
			.sorted(Comparator.comparing(
				(ReviewFilterUserView user) -> user.name() == null ? "" : user.name()
			).thenComparing(ReviewFilterUserView::id))
			.limit(50)
			.toList();
	}

	public Page<TaskItem> pool(Pageable pageable, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		return actor.role() == UserRole.ADMIN
			? items.findAllReviewPending(pageable)
			: items.findReviewPool(pageable);
	}

	public List<TaskItem> claimBatch(int count, String operationId, PlatformPrincipal actor) {
		throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_ID_REQUIRED", "请先选择审核任务");
	}

	public List<TaskItem> claimBatch(String taskId, int count, String operationId, PlatformPrincipal actor) {
		requireReviewer(actor);
		if (count < 1 || count > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量领取数量必须为 1 到 100");
		}
		String batchOperation = requiredOperationId(operationId);
		List<TaskItem> claimed = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			var item = claimOptional(taskId, batchOperation + ":" + index, actor);
			if (item.isEmpty()) break;
			claimed.add(item.get());
		}
		if (claimed.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NO_REVIEW_ITEM", "当前没有可领取的待审核数据");
		}
		return claimed;
	}

	public TaskItem assign(
		String itemId, String reviewerId, String operationId, long expectedRevision, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		if (users == null) throw new IllegalStateException("user store required");
		IdentityUser reviewer = users.findById(reviewerId).orElse(null);
		if (reviewer == null || reviewer.role() != UserRole.REVIEWER || reviewer.status() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REVIEWER", "只能分配给启用状态的审核员");
		}
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.SUBMITTED || item.getReviewerId() != null
			|| item.getRevision() != expectedRevision) throw stale();
		ReviewAssignMutation mutation = new ReviewAssignMutation(
			itemId, reviewerId, reviewer.name(), actor.userId(), actorName(actor), UUID.randomUUID().toString(),
			expectedRevision, requiredOperationId(operationId), Instant.now(clock)
		);
		return items.assignReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	public List<BatchReviewResult> batchApprove(
		String operationId, List<BatchReviewCommand> commands, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		String batchId = requiredOperationId(operationId);
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchReviewResult> results = new ArrayList<>();
		for (int index = 0; index < commands.size(); index++) {
			BatchReviewCommand command = commands.get(index);
			try {
				TaskItem item = requireItem(command.itemId());
				if (item.getStatus() != TaskItemStatus.REVIEW_PENDING
					|| item.getReviewerId() == null || item.getReviewAssignmentId() == null
					|| item.getRevision() != command.expectedRevision()) throw stale();
				TaskItemResult current = item.getCurrentResult();
				String finalAnswer = reviewFinalAnswer(requireConfiguration(item), current, command.text());
				Instant occurredAt = Instant.now(clock);
				AdminReviewApproveMutation mutation = new AdminReviewApproveMutation(
					item.getId(), actor.userId(), actorName(actor), command.expectedRevision(),
					batchId + ":" + index, current, finalAnswer,
					latestSubmissionOperationId(item),
					com.recording.platform.task.service.FirstCompletionTimePolicy.resolve(
						item.getFirstCompletedAt(), TaskItemStatus.COMPLETED, occurredAt
					),
					occurredAt
				);
				TaskItem updated = items.adminApproveReviewIfCurrent(mutation).orElseThrow(this::stale);
				results.add(BatchReviewResult.success(item.getId(), updated.getRevision()));
			} catch (ApiException exception) {
				results.add(BatchReviewResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			}
		}
		return results;
	}

	public List<BatchReviewResult> batchClaim(
		String operationId, List<BatchReviewCommand> commands, PlatformPrincipal actor
	) {
		requireReviewAccess(actor);
		return batchItems(operationId, commands, (command, index) ->
			claimItem(command.itemId(), requiredOperationId(operationId) + ":" + index,
				command.expectedRevision(), actor)
		);
	}

	public List<BatchReviewResult> batchAssign(
		String operationId, String reviewerId, List<BatchReviewCommand> commands, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		return batchItems(operationId, commands, (command, index) ->
			assign(command.itemId(), reviewerId, requiredOperationId(operationId) + ":" + index,
				command.expectedRevision(), actor)
		);
	}

	private List<BatchReviewResult> batchItems(
		String operationId,
		List<BatchReviewCommand> commands,
		java.util.function.BiFunction<BatchReviewCommand, Integer, TaskItem> mutation
	) {
		requiredOperationId(operationId);
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchReviewResult> results = new ArrayList<>();
		for (int index = 0; index < commands.size(); index++) {
			BatchReviewCommand command = commands.get(index);
			try {
				TaskItem updated = mutation.apply(command, index);
				results.add(BatchReviewResult.success(command.itemId(), updated.getRevision()));
			} catch (ApiException exception) {
				results.add(BatchReviewResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			}
		}
		return results;
	}

	private java.util.Optional<TaskItem> claimOptional(String taskId, String operationId, PlatformPrincipal actor) {
		String assignmentId = UUID.randomUUID().toString();
		ReviewClaimMutation mutation = new ReviewClaimMutation(
			taskId, actor.userId(), actorName(actor), assignmentId, operationId, Instant.now(clock)
		);
		return items.claimReview(mutation);
	}

	public TaskItem release(
		String itemId,
		String operationId,
		long expectedRevision,
		PlatformPrincipal actor
	) {
		requireReviewAccess(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING
			|| item.getRevision() != expectedRevision
			|| !actor.userId().equals(item.getReviewerId())
			|| item.getReviewAssignmentId() == null) {
			throw stale();
		}
		ReviewReleaseMutation mutation = new ReviewReleaseMutation(
			itemId, actor.userId(), actorName(actor), item.getReviewAssignmentId(), expectedRevision,
			requiredOperationId(operationId), Instant.now(clock)
		);
		return items.releaseReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	public TaskItem approve(
		String itemId, String operationId, long expectedRevision, String text, PlatformPrincipal actor
	) {
		TaskItem item = requireDecisionItem(itemId, expectedRevision, actor);
		TaskItemResult current = item.getCurrentResult();
		String finalAnswer = reviewFinalAnswer(requireConfiguration(item), current, text);
		return decide(
			item, operationId, actor, TaskItemStatus.COMPLETED, current, finalAnswer, "审核通过", null
		);
	}

	private String reviewFinalAnswer(
		TaskConfiguration configuration, TaskItemResult current, String requestedText
	) {
		String normalizedText = trimToNull(requestedText);
		if (configuration.getResultType() == TaskResultType.TEXT) {
			String finalText = normalizedText == null && current != null ? trimToNull(current.text()) : normalizedText;
			if (finalText == null) {
				throw new ApiException(
					HttpStatus.UNPROCESSABLE_ENTITY,
					"REVIEW_FINAL_ANSWER_REQUIRED",
					"文本任务必须填写审核最终答案"
				);
			}
			return finalText;
		}
		if (configuration.getResultType() == TaskResultType.AUDIO) {
			if (current == null || current.audio() == null) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_REQUIRED", "音频成果不能为空");
			}
			return normalizedText;
		}
		throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RESULT_TYPE_REQUIRED", "任务未配置最终成果类型");
	}

	public TaskItem reject(
		String itemId, String operationId, long expectedRevision,
		List<String> reasons, String note, PlatformPrincipal actor
	) {
		TaskItem item = requireDecisionItem(itemId, expectedRevision, actor);
		TaskConfiguration configuration = requireConfiguration(item);
		List<String> normalizedReasons = reasons == null ? List.of() : reasons.stream()
			.map(this::trimToNull).filter(java.util.Objects::nonNull).distinct().toList();
		for (String reason : normalizedReasons) {
			if (!configuration.getRejectionReasons().contains(reason)) {
				throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REJECTION_REASON", "包含任务未配置的驳回原因");
			}
		}
		String normalizedNote = trimToNull(note);
		if (normalizedReasons.isEmpty() && normalizedNote == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REJECTION_REASON_REQUIRED", "请选择驳回原因或填写补充说明");
		}
		String conclusion = String.join("、", normalizedReasons);
		if (normalizedNote != null) conclusion += (conclusion.isEmpty() ? "" : "；") + normalizedNote;
		CurrentRejection rejection = new CurrentRejection(
			normalizedReasons, normalizedNote, Instant.now(clock), actor.userId(), actorName(actor)
		);
		return decide(
			item, operationId, actor, TaskItemStatus.REWORK_PENDING,
			item.getCurrentResult(), null, conclusion, rejection
		);
	}

	private TaskItem decide(
		TaskItem item, String operationId, PlatformPrincipal actor, TaskItemStatus target,
		TaskItemResult result, String reviewFinalAnswer, String conclusion, CurrentRejection currentRejection
	) {
		Instant occurredAt = Instant.now(clock);
		Instant firstCompletedAt = com.recording.platform.task.service.FirstCompletionTimePolicy.resolve(
			item.getFirstCompletedAt(), target, occurredAt
		);
		if (actor.role() == UserRole.ADMIN) {
			AdminReviewDecisionMutation mutation = new AdminReviewDecisionMutation(
				item.getId(), actor.userId(), actorName(actor), item.getRevision(),
				requiredOperationId(operationId), target, result, reviewFinalAnswer, conclusion,
				currentRejection, latestSubmissionOperationId(item), firstCompletedAt, occurredAt
			);
			return items.adminDecideReviewIfCurrent(mutation).orElseThrow(this::stale);
		}
		ReviewDecisionMutation mutation = new ReviewDecisionMutation(
			item.getId(), actor.userId(), actorName(actor), item.getReviewAssignmentId(), item.getRevision(),
			requiredOperationId(operationId), target, result, reviewFinalAnswer, conclusion, currentRejection,
			latestSubmissionOperationId(item), firstCompletedAt, occurredAt
		);
		return items.decideReviewIfCurrent(mutation).orElseThrow(this::stale);
	}

	private TaskItem requireDecisionItem(String itemId, long expectedRevision, PlatformPrincipal actor) {
		requireReviewAccess(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != expectedRevision
			|| item.getReviewerId() == null || item.getReviewAssignmentId() == null) {
			throw stale();
		}
		if (actor.role() == UserRole.REVIEWER
			&& (!actor.userId().equals(item.getReviewerId()) || item.getReviewAssignmentId() == null)) {
			throw stale();
		}
		return item;
	}

	private TaskItem requireAssigned(String itemId, long expectedRevision, PlatformPrincipal actor) {
		requireReviewer(actor);
		TaskItem item = requireItem(itemId);
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING || item.getRevision() != expectedRevision
			|| !actor.userId().equals(item.getReviewerId()) || item.getReviewAssignmentId() == null) throw stale();
		return item;
	}

	private TaskConfiguration requireConfiguration(TaskItem item) {
		if (tasks == null) throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
		TaskRecord task = tasks.findById(item.getTaskId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		TaskConfiguration configuration = task.getConfiguration();
		if (configuration == null) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		}
		if (!configuration.isHumanReviewEnabled()) throw stale();
		return configuration;
	}

	private String latestSubmissionOperationId(TaskItem item) {
		if (item.getSubmissions() == null || item.getSubmissions().isEmpty()) return null;
		return item.getSubmissions().get(item.getSubmissions().size() - 1).getOperationId();
	}

	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

	private boolean matchesQuery(IdentityUser user, String query) {
		if (query.isEmpty()) return true;
		return java.util.stream.Stream.of(user.id(), user.name(), user.loginName())
			.filter(java.util.Objects::nonNull)
			.map(value -> value.toLowerCase(java.util.Locale.ROOT))
			.anyMatch(value -> value.contains(query));
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}

	private void requireReviewer(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.REVIEWER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private void requireReviewAccess(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.REVIEWER && actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private String requiredOperationId(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		}
		return value.trim();
	}

	private String actorName(PlatformPrincipal actor) {
		return actor.username() == null ? actor.name() : actor.username();
	}

	private ApiException stale() {
		return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "条目状态或修订号已变化");
	}
}

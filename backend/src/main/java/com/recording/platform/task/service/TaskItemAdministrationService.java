package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.AdminItemTransitionMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Service
public class TaskItemAdministrationService {
	private final TaskItemStore items;
	private final TaskStore tasks;
	private final Clock clock;

	public TaskItemAdministrationService(TaskItemStore items, TaskStore tasks, Clock clock) {
		this.items = items;
		this.tasks = tasks;
		this.clock = clock;
	}

	public TaskItem changeStatus(
		String itemId, TaskItemStatus target, String collectorId, String operationId,
		long expectedRevision, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		TaskItem item = requireCurrent(itemId, expectedRevision);
		if (target == null) throw invalid("STATUS_REQUIRED", "目标状态不能为空");
		if (target == TaskItemStatus.AVAILABLE) throw invalid("RELEASE_REQUIRED", "返回待领取必须使用释放操作");
		if (target == TaskItemStatus.DISCARDED) throw invalid("DISCARD_REQUIRED", "废弃数据必须使用废弃操作");
		if (item.getStatus() == TaskItemStatus.DISCARDED) throw stale();
		if (target == TaskItemStatus.COMPLETED && requireConfiguration(item).isHumanReviewEnabled()) {
			throw invalid("REVIEW_DECISION_REQUIRED", "启用人工审核的任务必须通过审核决定完成");
		}
		validateEnabled(item, target);
		String targetCollector = item.getCollectorId();
		String assignmentId = item.getAssignmentId();
		if (target == TaskItemStatus.RECORDING_PENDING && targetCollector == null) {
			targetCollector = trimToNull(collectorId);
			if (targetCollector == null) throw invalid("COLLECTOR_REQUIRED", "调整到待录制必须指定采集员");
			assignmentId = UUID.randomUUID().toString();
		}
		AdminItemTransitionMutation mutation = mutation(
			item, target, targetCollector, assignmentId, operationId, actor
		);
		return items.adminTransitionIfCurrent(mutation).orElseThrow(this::stale);
	}

	public TaskItem discard(
		String itemId, String operationId, long expectedRevision, String reason, PlatformPrincipal actor
	) {
		requireAdminOrCollector(actor);
		TaskItem item = requireCurrent(itemId, expectedRevision);
		if (item.getStatus() == TaskItemStatus.DISCARDED) throw stale();
		String normalizedReason = trimToNull(reason);
		if (actor.role() == UserRole.COLLECTOR) {
			if (!actor.userId().equals(item.getCollectorId())) throw forbidden();
			if (item.getStatus() != TaskItemStatus.RECORDING_PENDING
				&& item.getStatus() != TaskItemStatus.REWORK_PENDING) {
				throw invalid("INVALID_DISCARD_STATE", "只有待录制或待返工数据可以标记为无效");
			}
			if (normalizedReason == null || normalizedReason.length() > 200) {
				throw invalid("INVALID_DISCARD_REASON", "无效原因必须为 1 到 200 个字符");
			}
		} else if (normalizedReason == null) {
			normalizedReason = "管理员标记为无效数据";
		} else if (normalizedReason.length() > 200) {
			throw invalid("INVALID_DISCARD_REASON", "无效原因不能超过 200 个字符");
		}
		return items.adminDiscardIfCurrent(mutation(
			item, TaskItemStatus.DISCARDED, item.getCollectorId(), item.getAssignmentId(),
			operationId, normalizedReason, actor
		)).orElseThrow(this::stale);
	}

	public TaskItem discard(String itemId, String operationId, long expectedRevision, PlatformPrincipal actor) {
		return discard(itemId, operationId, expectedRevision, null, actor);
	}

	public TaskItem restore(String itemId, String operationId, long expectedRevision, PlatformPrincipal actor) {
		requireAdminOrCollector(actor);
		TaskItem item = requireCurrent(itemId, expectedRevision);
		if (item.getStatus() != TaskItemStatus.DISCARDED || item.getDiscardedPreviousStatus() == null) throw stale();
		if (actor.role() == UserRole.COLLECTOR) {
			if (!actor.userId().equals(item.getCollectorId())) throw forbidden();
			if (item.getDiscardedPreviousStatus() != TaskItemStatus.RECORDING_PENDING
				&& item.getDiscardedPreviousStatus() != TaskItemStatus.REWORK_PENDING) {
				throw invalid("INVALID_RESTORE_STATE", "采集员只能恢复本人待录制或待返工数据");
			}
		}
		validateEnabled(item, item.getDiscardedPreviousStatus());
		return items.adminRestoreIfCurrent(mutation(
			item, item.getDiscardedPreviousStatus(), item.getCollectorId(), item.getAssignmentId(),
			operationId, null, actor
		)).orElseThrow(this::stale);
	}

	public List<BatchItemResult> batchChangeStatus(
		String operationId, TaskItemStatus target, List<BatchItemCommand> commands, PlatformPrincipal actor
	) {
		return batch(operationId, commands, actor, (command, itemOperation) ->
			changeStatus(command.itemId(), target, command.collectorId(), itemOperation, command.expectedRevision(), actor)
		);
	}

	public List<BatchItemResult> batchDiscard(
		String operationId, List<BatchItemCommand> commands, PlatformPrincipal actor
	) {
		return batch(operationId, commands, actor, (command, itemOperation) ->
			discard(command.itemId(), itemOperation, command.expectedRevision(), null, actor)
		);
	}

	public List<BatchItemResult> batchRestore(
		String operationId, List<BatchItemCommand> commands, PlatformPrincipal actor
	) {
		return batch(operationId, commands, actor, (command, itemOperation) ->
			restore(command.itemId(), itemOperation, command.expectedRevision(), actor)
		);
	}

	private List<BatchItemResult> batch(
		String operationId,
		List<BatchItemCommand> commands,
		PlatformPrincipal actor,
		BiFunction<BatchItemCommand, String, TaskItem> action
	) {
		requireAdmin(actor);
		String batchId = required(operationId);
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw invalid("INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchItemResult> results = new ArrayList<>();
		for (int index = 0; index < commands.size(); index++) {
			BatchItemCommand command = commands.get(index);
			try {
				TaskItem updated = action.apply(command, batchId + ":" + index);
				results.add(BatchItemResult.success(command.itemId(), updated.getRevision()));
			} catch (ApiException exception) {
				results.add(BatchItemResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			} catch (org.springframework.dao.DuplicateKeyException exception) {
				results.add(BatchItemResult.failure(command.itemId(), "COLLECTOR_BUSY", "采集员已有待录制数据"));
			}
		}
		return results;
	}

	private void validateEnabled(TaskItem item, TaskItemStatus target) {
		TaskConfiguration configuration = requireConfiguration(item);
		if (target == TaskItemStatus.REVIEW_PENDING) {
			throw invalid("REVIEW_CLAIM_REQUIRED", "待审核状态只能通过审核领取或分配进入");
		}
		if (target == TaskItemStatus.SUBMITTED && !configuration.isHumanReviewEnabled()
			|| target == TaskItemStatus.AI_PROCESSING && !configuration.isAiEnabled()) {
			throw invalid("STATUS_NOT_ENABLED", "任务未启用该状态阶段");
		}
	}

	private TaskConfiguration requireConfiguration(TaskItem item) {
		TaskRecord task = tasks.findById(item.getTaskId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (task.getConfiguration() == null) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		}
		return task.getConfiguration();
	}

	private AdminItemTransitionMutation mutation(
		TaskItem item, TaskItemStatus target, String collectorId, String assignmentId,
		String operationId, PlatformPrincipal actor
	) {
		return mutation(item, target, collectorId, assignmentId, operationId, null, actor);
	}

	private AdminItemTransitionMutation mutation(
		TaskItem item, TaskItemStatus target, String collectorId, String assignmentId,
		String operationId, String reason, PlatformPrincipal actor
	) {
		Instant occurredAt = Instant.now(clock);
		return new AdminItemTransitionMutation(
			item.getId(), actor.userId(), actorName(actor), item.getRevision(), required(operationId),
			item.getStatus(), target, collectorId, assignmentId, occurredAt,
			FirstCompletionTimePolicy.resolve(item.getFirstCompletedAt(), target, occurredAt),
			reason, actor.role()
		);
	}

	private TaskItem requireCurrent(String itemId, long revision) {
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
		if (item.getRevision() != revision) throw stale();
		return item;
	}

	private void requireAdmin(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}
	private void requireAdminOrCollector(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.COLLECTOR) throw forbidden();
	}
	private String required(String value) {
		if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		return value.trim();
	}
	private String actorName(PlatformPrincipal actor) { return actor.name() == null ? actor.username() : actor.name(); }
	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作"); }
	private ApiException stale() { return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "条目状态或修订号已变化"); }
	private ApiException invalid(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message); }
}

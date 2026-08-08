package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.AdminItemTransitionMutation;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemCollectorAssignmentService {
	private final TaskItemStore items;
	private final TaskStore tasks;
	private final TaskGrantStore grants;
	private final IdentityDirectory users;
	private final Clock clock;

	public TaskItemCollectorAssignmentService(
		TaskItemStore items,
		TaskStore tasks,
		TaskGrantStore grants,
		IdentityDirectory users,
		Clock clock
	) {
		this.items = items;
		this.tasks = tasks;
		this.grants = grants;
		this.users = users;
		this.clock = clock;
	}

	public TaskItem assign(
		String itemId, String collectorId, String operationId, long expectedRevision, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		String targetCollectorId = requiredCollector(collectorId);
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
		if (item.getRevision() != expectedRevision || item.getStatus() != TaskItemStatus.AVAILABLE) throw stale();
		validateTarget(item.getTaskId(), targetCollectorId, actor);
		Instant occurredAt = Instant.now(clock);
		AdminItemTransitionMutation mutation = new AdminItemTransitionMutation(
			item.getId(), actor.userId(), actorName(actor), item.getRevision(), requiredOperation(operationId),
			TaskItemStatus.AVAILABLE, TaskItemStatus.RECORDING_PENDING,
			targetCollectorId, UUID.randomUUID().toString(), occurredAt, null, actor.role()
		);
		return items.adminTransitionIfCurrent(mutation).orElseThrow(this::stale);
	}

	public void validateTarget(String taskId, String collectorId, PlatformPrincipal actor) {
		requireAdmin(actor);
		String targetCollectorId = requiredCollector(collectorId);
		TaskRecord task = tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (task.getLifecycle() != TaskLifecycle.RUNNING) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_NOT_RUNNING", "只有进行中的任务可以分配");
		}
		IdentityUser collector = users.findById(targetCollectorId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (collector.role() != UserRole.COLLECTOR || collector.status() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COLLECTOR", "录音人员不可用");
		}
		if (grants.findActive(taskId, targetCollectorId).isEmpty()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TASK_GRANT_REQUIRED", "没有该任务的有效授权");
		}
	}

	public List<BatchItemResult> batchAssign(
		String operationId, String collectorId, List<BatchItemCommand> commands, PlatformPrincipal actor
	) {
		requireAdmin(actor);
		String batchId = requiredOperation(operationId);
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchItemResult> results = new ArrayList<>();
		for (int index = 0; index < commands.size(); index++) {
			BatchItemCommand command = commands.get(index);
			try {
				TaskItem updated = assign(
					command.itemId(), collectorId, batchId + ":" + index, command.expectedRevision(), actor
				);
				results.add(BatchItemResult.success(command.itemId(), updated.getRevision()));
			} catch (ApiException exception) {
				results.add(BatchItemResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			}
		}
		return results;
	}

	private void requireAdmin(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private String requiredCollector(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COLLECTOR_REQUIRED", "collectorId 不能为空");
		}
		return value.trim();
	}

	private String requiredOperation(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		}
		return value.trim();
	}

	private String actorName(PlatformPrincipal actor) {
		return actor.name() == null ? actor.username() : actor.name();
	}

	private ApiException stale() {
		return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "条目状态或修订号已变化");
	}
}

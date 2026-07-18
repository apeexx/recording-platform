package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.model.TaskItemStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemQueryService {
	private final TaskItemStore items;
	private final TaskStore tasks;
	public TaskItemQueryService(TaskItemStore items, TaskStore tasks) {
		this.items = items;
		this.tasks = tasks;
	}

	public PageResponse<CollectorTaskItemView> mine(
		String taskId, CollectorWorkKind kind, int page, int size, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.COLLECTOR) throw forbidden();
		CollectorWorkKind normalizedKind = kind == null ? CollectorWorkKind.ALL : kind;
		List<TaskItemStatus> statuses = switch (normalizedKind) {
			case ALL -> List.of(TaskItemStatus.REWORK_PENDING, TaskItemStatus.RECORDING_PENDING);
			case RECORDING -> List.of(TaskItemStatus.RECORDING_PENDING);
			case REWORK -> List.of(TaskItemStatus.REWORK_PENDING);
		};
		PageRequest pageable = PageRequest.of(
			Math.max(page, 0), Math.min(Math.max(size, 1), 100),
			Sort.by(Sort.Order.desc("status"), Sort.Order.desc("updatedAt"), Sort.Order.asc("sequence"))
		);
		var result = items.findAllByCollectorIdAndStatusIn(actor.userId(), taskId, statuses, pageable);
		var filtered = result.getContent();
		Map<String, com.recording.platform.task.model.TaskRecord> taskMap = tasks.findAllByIdIn(
			filtered.stream().map(TaskItem::getTaskId).distinct().toList()
		).stream().collect(Collectors.toMap(com.recording.platform.task.model.TaskRecord::getId, Function.identity()));
		List<CollectorTaskItemView> views = filtered.stream()
			.map(item -> CollectorTaskItemView.from(item, taskMap.get(item.getTaskId()))).toList();
		return new PageResponse<>(views, pageable.getPageNumber(), pageable.getPageSize(),
			result.getTotalElements());
	}

	public TaskItem get(String itemId, PlatformPrincipal actor) {
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
		if (actor == null) throw forbidden();
		if (actor.role() == UserRole.ADMIN || actor.role() == UserRole.REVIEWER) return item;
		if (actor.role() == UserRole.COLLECTOR && actor.userId().equals(item.getCollectorId())) return item;
		throw forbidden();
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "TASK_ITEM_ACCESS_DENIED", "没有权限读取该任务条目");
	}
}

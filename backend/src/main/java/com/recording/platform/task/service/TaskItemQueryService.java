package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.TaskItemStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemQueryService {
	private final TaskItemStore items;
	public TaskItemQueryService(TaskItemStore items) {
		this.items = items;
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

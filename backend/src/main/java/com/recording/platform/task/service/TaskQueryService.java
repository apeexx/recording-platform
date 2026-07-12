package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskAccessRequestStore;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {
	private final TaskStore tasks;
	private final TaskGrantStore grants;
	private final TaskAccessRequestStore requests;

	public TaskQueryService(TaskStore tasks, TaskGrantStore grants, TaskAccessRequestStore requests) {
		this.tasks = tasks;
		this.grants = grants;
		this.requests = requests;
	}

	public PageResponse<TaskView> list(PlatformPrincipal actor, int page, int size) {
		requireAuthenticated(actor);
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
		if (actor.role() != UserRole.COLLECTOR) {
			Page<TaskView> result = tasks.findAll(pageable).map((task) -> TaskView.from(task, null));
			return PageResponse.from(result);
		}
		Page<TaskRecord> visible = tasks.findAllCollectorVisible(pageable);
		List<TaskView> views = visible.getContent().stream().map((task) -> {
			String permission = grants.findActive(task.getId(), actor.userId()).isPresent()
				? "ACTIVE"
				: requests.findPending(task.getId(), actor.userId()).isPresent() ? "PENDING" : "NONE";
			return TaskView.from(task, permission);
		}).toList();
		return new PageResponse<>(views, pageable.getPageNumber(), pageable.getPageSize(), visible.getTotalElements());
	}

	public TaskView get(String taskId, PlatformPrincipal actor) {
		requireAuthenticated(actor);
		TaskRecord task = tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (actor.role() == UserRole.COLLECTOR && grants.findActive(taskId, actor.userId()).isEmpty()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TASK_GRANT_REQUIRED", "没有该任务的有效授权");
		}
		return TaskView.from(task, actor.role() == UserRole.COLLECTOR ? "ACTIVE" : null);
	}

	private void requireAuthenticated(PlatformPrincipal actor) {
		if (actor == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
	}

	public record TaskView(
		String id,
		String taskCode,
		String platformId,
		String name,
		String description,
		com.recording.platform.task.model.TaskLifecycle lifecycle,
		String currentVersionId,
		int currentVersionNumber,
		String permissionStatus
	) {
		static TaskView from(TaskRecord task, String permissionStatus) {
			return new TaskView(
				task.getId(), task.getTaskCode(), task.getPlatformId(), task.getName(), task.getDescription(),
				task.getLifecycle(), task.getCurrentVersionId(), task.getCurrentVersionNumber(), permissionStatus
			);
		}
	}
}

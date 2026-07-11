package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {
	private final TaskStore tasks;
	private final TaskGrantStore grants;

	public TaskQueryService(TaskStore tasks, TaskGrantStore grants) {
		this.tasks = tasks;
		this.grants = grants;
	}

	public PageResponse<TaskView> list(PlatformPrincipal actor, int page, int size) {
		requireAuthenticated(actor);
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
		if (actor.role() != UserRole.COLLECTOR) {
			Page<TaskView> result = tasks.findAll(pageable).map((task) -> TaskView.from(task, null));
			return PageResponse.from(result);
		}
		Page<TaskGrant> grantPage = grants.findAllActiveByUserId(actor.userId(), pageable);
		List<String> ids = grantPage.getContent().stream().map(TaskGrant::getTaskId).toList();
		Map<String, TaskRecord> byId = new LinkedHashMap<>();
		for (TaskRecord task : tasks.findAllByIdIn(ids)) byId.put(task.getId(), task);
		List<TaskView> views = ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
			.map((task) -> TaskView.from(task, "ACTIVE")).toList();
		return new PageResponse<>(views, pageable.getPageNumber(), pageable.getPageSize(), grantPage.getTotalElements());
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

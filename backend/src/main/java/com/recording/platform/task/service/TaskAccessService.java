package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import com.recording.platform.api.PageResponse;

@Service
public class TaskAccessService {
	private final TaskStore tasks;
	private final UserStore users;
	private final TaskGrantStore grants;
	private final TaskAccessRequestStore requests;
	private final Clock clock;

	public TaskAccessService(
		TaskStore tasks,
		UserStore users,
		TaskGrantStore grants,
		TaskAccessRequestStore requests,
		Clock clock
	) {
		this.tasks = tasks;
		this.users = users;
		this.grants = grants;
		this.requests = requests;
		this.clock = clock;
	}

	public TaskAccessRequest requestAccess(String taskId, PlatformPrincipal actor) {
		requireRole(actor, UserRole.COLLECTOR);
		requireTask(taskId);
		requireActiveCollector(actor.userId());
		if (grants.findActive(taskId, actor.userId()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_GRANTED", "已拥有该任务权限");
		}
		TaskAccessRequest pending = requests.findPending(taskId, actor.userId()).orElse(null);
		if (pending != null) return pending;
		try {
			Instant now = Instant.now(clock);
			TaskAccessRequest request = new TaskAccessRequest();
			request.setTaskId(taskId);
			request.setUserId(actor.userId());
			request.setStatus(AccessRequestStatus.PENDING);
			request.setCreatedAt(now);
			request.setUpdatedAt(now);
			return requests.save(request);
		} catch (DuplicateKeyException exception) {
			return requests.findPending(taskId, actor.userId())
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCESS_REQUEST_CONFLICT", "权限申请状态已变化"));
		}
	}

	public TaskGrant approve(String requestId, PlatformPrincipal actor) {
		return approve(null, requestId, actor);
	}

	public TaskGrant approve(String taskId, String requestId, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		TaskAccessRequest request = requireRequest(requestId);
		requireRequestTask(taskId, request);
		if (request.getStatus() == AccessRequestStatus.REJECTED) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCESS_REQUEST_DECIDED", "申请已被驳回");
		}
		if (request.getStatus() == AccessRequestStatus.APPROVED) return existingApprovedGrant(request, actor);
		Instant now = Instant.now(clock);
		TaskAccessRequest decided = requests.decideIfPending(
			requestId, AccessRequestStatus.APPROVED, actor.userId(), null, now
		).orElse(null);
		if (decided != null) {
			return grants.activate(decided.getTaskId(), decided.getUserId(), actor.userId(), now);
		}
		TaskAccessRequest current = requireRequest(requestId);
		if (current.getStatus() == AccessRequestStatus.REJECTED) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCESS_REQUEST_DECIDED", "申请已被驳回");
		}
		return existingApprovedGrant(current, actor);
	}

	public TaskGrant grant(String taskId, String userId, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		requireTask(taskId);
		requireActiveCollector(userId);
		return grants.activate(taskId, userId, actor.userId(), Instant.now(clock));
	}

	public PageResponse<TaskGrant> listGrants(String taskId, int page, int size, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		return PageResponse.from(grants.findAllByTaskId(taskId, page(page, size)));
	}

	public PageResponse<TaskAccessRequest> listRequests(String taskId, int page, int size, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		return PageResponse.from(requests.findAllByTaskId(taskId, page(page, size)));
	}

	public TaskAccessRequest reject(String requestId, String reason, PlatformPrincipal actor) {
		return reject(null, requestId, reason, actor);
	}

	public TaskAccessRequest reject(String taskId, String requestId, String reason, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		TaskAccessRequest request = requireRequest(requestId);
		requireRequestTask(taskId, request);
		if (request.getStatus() != AccessRequestStatus.PENDING) return request;
		String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
		return requests.decideIfPending(
			requestId,
			AccessRequestStatus.REJECTED,
			actor.userId(),
			normalizedReason,
			Instant.now(clock)
		).orElseGet(() -> requireRequest(requestId));
	}

	public TaskGrant revoke(String taskId, String userId, PlatformPrincipal actor) {
		requireRole(actor, UserRole.ADMIN);
		TaskGrant grant = grants.findByTaskIdAndUserId(taskId, userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_GRANT_NOT_FOUND", "任务授权不存在"));
		if (grant.getStatus() != GrantStatus.REVOKED) {
			grant.setStatus(GrantStatus.REVOKED);
			grant.setUpdatedAt(Instant.now(clock));
			grant = grants.save(grant);
		}
		return grant;
	}

	private void requireTask(String taskId) {
		if (tasks.findById(taskId).isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
		}
	}

	private TaskAccessRequest requireRequest(String requestId) {
		return requests.findById(requestId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCESS_REQUEST_NOT_FOUND", "权限申请不存在"));
	}

	private void requireRequestTask(String taskId, TaskAccessRequest request) {
		if (taskId != null && !taskId.equals(request.getTaskId())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ACCESS_REQUEST_NOT_FOUND", "权限申请不存在");
		}
	}

	private TaskGrant existingApprovedGrant(TaskAccessRequest request, PlatformPrincipal actor) {
		return grants.findByTaskIdAndUserId(request.getTaskId(), request.getUserId())
			.orElseGet(() -> grants.activate(
				request.getTaskId(), request.getUserId(), actor.userId(), Instant.now(clock)
			));
	}

	private void requireActiveCollector(String userId) {
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() != UserRole.COLLECTOR || user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COLLECTOR", "录音人员不可用");
		}
	}

	private void requireRole(PlatformPrincipal actor, UserRole role) {
		if (actor == null || actor.role() != role) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private PageRequest page(int page, int size) {
		return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
	}
}

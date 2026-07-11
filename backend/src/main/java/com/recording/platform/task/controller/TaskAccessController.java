package com.recording.platform.task.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.service.TaskAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/{taskId}")
public class TaskAccessController {
	private final TaskAccessService access;
	private final IdempotencyService idempotency;
	public TaskAccessController(TaskAccessService access, IdempotencyService idempotency) {
		this.access = access;
		this.idempotency = idempotency;
	}

	@PostMapping("/access-requests") @ResponseStatus(HttpStatus.CREATED)
	public TaskAccessRequest request(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication, "access:request:" + taskId, operationId, TaskAccessRequest.class,
			() -> access.requestAccess(taskId, actor)
		);
	}
	@GetMapping("/access-requests")
	public PageResponse<TaskAccessRequest> requests(
		@PathVariable String taskId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) { return access.listRequests(taskId, page, size, actor); }
	@PostMapping("/access-requests/{requestId}/approve")
	public TaskGrant approve(
		@PathVariable String taskId,
		@PathVariable String requestId,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) { return idempotency.execute(
		authentication, "access:approve:" + taskId + ":" + requestId, operationId, TaskGrant.class,
		() -> access.approve(taskId, requestId, actor)
	); }
	@PostMapping("/access-requests/{requestId}/reject")
	public TaskAccessRequest reject(
		@PathVariable String taskId,
		@PathVariable String requestId,
		@RequestBody(required = false) DecisionRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) { return idempotency.execute(
		authentication, "access:reject:" + taskId + ":" + requestId, operationId, TaskAccessRequest.class,
		() -> access.reject(taskId, requestId, request == null ? null : request.reason(), actor)
	); }

	@PostMapping("/grants") @ResponseStatus(HttpStatus.CREATED)
	public TaskGrant grant(
		@PathVariable String taskId,
		@Valid @RequestBody GrantRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) { return idempotency.execute(
		authentication, "access:grant:" + taskId + ":" + request.userId(), operationId, TaskGrant.class,
		() -> access.grant(taskId, request.userId(), actor)
	); }
	@GetMapping("/grants")
	public PageResponse<TaskGrant> grants(
		@PathVariable String taskId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) { return access.listGrants(taskId, page, size, actor); }
	@DeleteMapping("/grants/{userId}")
	public TaskGrant revoke(
		@PathVariable String taskId,
		@PathVariable String userId,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) { return idempotency.execute(
		authentication, "access:revoke:" + taskId + ":" + userId, operationId, TaskGrant.class,
		() -> access.revoke(taskId, userId, actor)
	); }

	public record GrantRequest(@NotNull String userId) { }
	public record DecisionRequest(String reason) { }
}

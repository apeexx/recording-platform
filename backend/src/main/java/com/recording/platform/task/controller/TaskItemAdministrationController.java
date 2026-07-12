package com.recording.platform.task.controller;

import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import com.recording.platform.importing.TaskItemActionService;
import com.recording.platform.idempotency.IdempotencyService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/task-items")
public class TaskItemAdministrationController {
	private final TaskItemAdministrationService administration;
	private final TaskItemActionService actions;
	private final IdempotencyService idempotency;

	public TaskItemAdministrationController(
		TaskItemAdministrationService administration,
		TaskItemActionService actions
	) {
		this(administration, actions, null);
	}

	@Autowired
	public TaskItemAdministrationController(
		TaskItemAdministrationService administration,
		TaskItemActionService actions,
		IdempotencyService idempotency
	) {
		this.administration = administration;
		this.actions = actions;
		this.idempotency = idempotency;
	}

	@PostMapping("/{itemId}/status")
	public TaskItem changeStatus(
		@PathVariable String itemId,
		@Valid @RequestBody StatusRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:status:" + itemId, request.operationId(), TaskItem.class, () -> administration.changeStatus(
			itemId, request.status(), request.collectorId(), request.operationId(), request.expectedRevision(), actor
		));
	}

	@PostMapping("/{itemId}/discard")
	public TaskItem discard(
		@PathVariable String itemId,
		@Valid @RequestBody ItemOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:discard:" + itemId, request.operationId(), TaskItem.class,
			() -> administration.discard(itemId, request.operationId(), request.expectedRevision(), actor));
	}

	@PostMapping("/{itemId}/restore")
	public TaskItem restore(
		@PathVariable String itemId,
		@Valid @RequestBody ItemOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:restore:" + itemId, request.operationId(), TaskItem.class,
			() -> administration.restore(itemId, request.operationId(), request.expectedRevision(), actor));
	}

	@PostMapping("/batch/status")
	public List<BatchItemResult> batchStatus(
		@Valid @RequestBody BatchStatusRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:batch-status", request.operationId(), new TypeReference<List<BatchItemResult>>() { },
			() -> administration.batchChangeStatus(
			request.operationId(), request.status(), commands(request.items()), actor
		));
	}

	@PostMapping("/batch/discard")
	public List<BatchItemResult> batchDiscard(
		@Valid @RequestBody BatchOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:batch-discard", request.operationId(), new TypeReference<List<BatchItemResult>>() { },
			() -> administration.batchDiscard(request.operationId(), commands(request.items()), actor));
	}

	@PostMapping("/batch/restore")
	public List<BatchItemResult> batchRestore(
		@Valid @RequestBody BatchOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:batch-restore", request.operationId(), new TypeReference<List<BatchItemResult>>() { },
			() -> administration.batchRestore(request.operationId(), commands(request.items()), actor));
	}

	@PostMapping("/batch/release")
	public List<BatchItemResult> batchRelease(
		@Valid @RequestBody BatchOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:batch-release", request.operationId(), new TypeReference<List<BatchItemResult>>() { },
			() -> actions.batchRelease(request.operationId(), commands(request.items()), actor));
	}

	private <T> T execute(String action, String key, Class<T> type, Supplier<T> mutation) {
		if (idempotency == null) return mutation.get();
		return idempotency.execute(SecurityContextHolder.getContext().getAuthentication(), action, key, type, mutation);
	}

	private <T> T execute(String action, String key, TypeReference<T> type, Supplier<T> mutation) {
		if (idempotency == null) return mutation.get();
		return idempotency.execute(SecurityContextHolder.getContext().getAuthentication(), action, key, type, mutation);
	}

	private List<BatchItemCommand> commands(List<BatchItemRequest> items) {
		return items.stream().map((item) ->
			new BatchItemCommand(item.itemId(), item.expectedRevision(), item.collectorId())
		).toList();
	}

	public record ItemOperationRequest(@NotBlank String operationId, @NotNull Long expectedRevision) { }
	public record StatusRequest(
		@NotBlank String operationId,
		@NotNull Long expectedRevision,
		@NotNull TaskItemStatus status,
		String collectorId
	) { }
	public record BatchItemRequest(
		@NotBlank String itemId,
		@NotNull Long expectedRevision,
		String collectorId
	) { }
	public record BatchOperationRequest(
		@NotBlank String operationId,
		@NotNull List<@Valid BatchItemRequest> items
	) { }
	public record BatchStatusRequest(
		@NotBlank String operationId,
		@NotNull TaskItemStatus status,
		@NotNull List<@Valid BatchItemRequest> items
	) { }
}

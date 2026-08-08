package com.recording.platform.task.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import com.recording.platform.task.service.TaskItemCollectorAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-items/batch")
public class TaskItemCollectorAssignmentController {
	private final TaskItemCollectorAssignmentService assignments;
	private final IdempotencyService idempotency;

	public TaskItemCollectorAssignmentController(TaskItemCollectorAssignmentService assignments) {
		this(assignments, null);
	}

	@Autowired
	public TaskItemCollectorAssignmentController(
		TaskItemCollectorAssignmentService assignments,
		IdempotencyService idempotency
	) {
		this.assignments = assignments;
		this.idempotency = idempotency;
	}

	@PostMapping("/assign-collector")
	public List<BatchItemResult> assign(
		@Valid @RequestBody AssignCollectorRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("item:batch-assign-collector", request.operationId(), () -> assignments.batchAssign(
			request.operationId(), request.collectorId(), request.items().stream()
				.map(item -> new BatchItemCommand(item.itemId(), item.expectedRevision(), null)).toList(),
			actor
		));
	}

	private List<BatchItemResult> execute(
		String action, String key, Supplier<List<BatchItemResult>> mutation
	) {
		if (idempotency == null) return mutation.get();
		return idempotency.execute(
			SecurityContextHolder.getContext().getAuthentication(), action, key,
			new TypeReference<List<BatchItemResult>>() { }, mutation
		);
	}

	public record AssignItemRequest(@NotBlank String itemId, @NotNull Long expectedRevision) { }

	public record AssignCollectorRequest(
		@NotBlank String operationId,
		@NotBlank String collectorId,
		@NotNull List<@Valid AssignItemRequest> items
	) { }
}

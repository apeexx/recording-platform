package com.recording.platform.task.controller;

import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.task.service.TaskDeletionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskDeletionController {
	private final TaskDeletionService deletion;
	private final IdempotencyService idempotency;

	public TaskDeletionController(TaskDeletionService deletion, IdempotencyService idempotency) {
		this.deletion = deletion;
		this.idempotency = idempotency;
	}

	@DeleteMapping("/{taskId}")
	public DeleteTaskResponse delete(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "task:delete:" + taskId, operationId, DeleteTaskResponse.class, () -> {
			deletion.deleteDraft(taskId);
			return new DeleteTaskResponse(taskId, true);
		});
	}

	public record DeleteTaskResponse(String taskId, boolean deleted) { }
}

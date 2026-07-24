package com.recording.platform.integration;

import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.importing.AddTaskItemCommand;
import com.recording.platform.importing.TaskItemCreationService;
import com.recording.platform.task.model.TaskItem;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/tasks/{taskId}/items")
public class IntegrationTaskItemController {
	private final TaskItemCreationService creation;
	private final IdempotencyService idempotency;

	public IntegrationTaskItemController(
		TaskItemCreationService creation,
		IdempotencyService idempotency
	) {
		this.creation = creation;
		this.idempotency = idempotency;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public IntegrationTaskItemResponse add(
		@PathVariable String taskId,
		@RequestBody AddItemRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication,
			"integration:item:add:" + taskId,
			operationId,
			IntegrationTaskItemResponse.class,
			() -> IntegrationTaskItemResponse.from(creation.addIntegration(taskId, request.command(), operationId))
		);
	}

	public record AddItemRequest(
		String referenceText,
		String referenceAudioUrl,
		String referenceVideoUrl
	) {
		AddTaskItemCommand command() {
			return new AddTaskItemCommand(referenceText, referenceAudioUrl, referenceVideoUrl);
		}
	}

	public record IntegrationTaskItemResponse(
		String itemId,
		String taskId,
		String itemCode,
		String status,
		Instant createdAt
	) {
		static IntegrationTaskItemResponse from(TaskItem item) {
			return new IntegrationTaskItemResponse(
				item.getId(),
				item.getTaskId(),
				item.getItemCode(),
				item.getStatus().name(),
				item.getCreatedAt()
			);
		}
	}
}

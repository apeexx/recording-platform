package com.recording.platform.task.controller;

import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskItemReferenceAdministrationService;
import com.recording.platform.task.service.UpdateTaskItemReferencesCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-items")
public class TaskItemReferenceAdministrationController {
	private final TaskItemReferenceAdministrationService administration;
	private final IdempotencyService idempotency;

	public TaskItemReferenceAdministrationController(
		TaskItemReferenceAdministrationService administration, IdempotencyService idempotency
	) {
		this.administration = administration;
		this.idempotency = idempotency;
	}

	@PutMapping("/{itemId}")
	public TaskItem update(
		@PathVariable String itemId,
		@Valid @RequestBody UpdateReferencesRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "task-item:edit:" + itemId, operationId, TaskItem.class, () ->
			administration.update(itemId, new UpdateTaskItemReferencesCommand(
				request.expectedRevision(), request.referenceText(),
				request.referenceAudioUrl(), request.referenceVideoUrl()
			), operationId, actor)
		);
	}

	@DeleteMapping("/{itemId}")
	public DeleteTaskItemResponse delete(
		@PathVariable String itemId,
		@RequestParam long expectedRevision,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication, "task-item:delete:" + itemId, operationId, DeleteTaskItemResponse.class, () -> {
				administration.delete(itemId, expectedRevision, operationId, actor);
				return new DeleteTaskItemResponse(itemId, true);
			}
		);
	}

	public record UpdateReferencesRequest(
		@NotNull Long expectedRevision,
		String referenceText,
		String referenceAudioUrl,
		String referenceVideoUrl
	) { }
	public record DeleteTaskItemResponse(String itemId, boolean deleted) { }
}

package com.recording.platform.task.controller;

import com.recording.platform.importing.SubmitTaskItemForm;
import com.recording.platform.importing.TaskItemActionService;
import com.recording.platform.importing.TaskItemSubmissionService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskItemQueryService;
import com.recording.platform.task.model.TaskItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/task-items")
public class TaskItemController {
	private final TaskItemSubmissionService submissions;
	private final TaskItemActionService actions;
	private final TaskItemQueryService queries;

	public TaskItemController(TaskItemSubmissionService submissions, TaskItemActionService actions, TaskItemQueryService queries) {
		this.submissions = submissions;
		this.actions = actions;
		this.queries = queries;
	}

	@GetMapping("/{itemId}")
	public TaskItem get(@PathVariable String itemId, @AuthenticationPrincipal PlatformPrincipal actor) {
		return queries.get(itemId, actor);
	}

	@PostMapping(value = "/{itemId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TaskItemActionResult submit(
		@PathVariable String itemId,
		@RequestParam String operationId,
		@RequestParam String assignmentId,
		@RequestParam long expectedRevision,
		@RequestParam(required = false) String text,
		@RequestPart(required = false) MultipartFile audio,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return submissions.submit(
			itemId,
			new SubmitTaskItemForm(operationId, assignmentId, expectedRevision, text),
			audio,
			actor
		);
	}

	@PostMapping("/{itemId}/release")
	public TaskItemActionResult release(
		@PathVariable String itemId,
		@Valid @RequestBody ItemOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return actions.release(itemId, request.operationId(), request.expectedRevision(), actor);
	}

	@PostMapping("/{itemId}/reject")
	public TaskItemActionResult reject(
		@PathVariable String itemId,
		@Valid @RequestBody RejectRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return actions.reject(itemId, request.operationId(), request.expectedRevision(), request.reason(), actor);
	}

	public record ItemOperationRequest(@NotNull String operationId, @NotNull Long expectedRevision) { }
	public record RejectRequest(@NotNull String operationId, @NotNull Long expectedRevision, @NotNull String reason) { }
}

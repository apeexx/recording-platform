package com.recording.platform.batch.controller;

import com.recording.platform.batch.model.BatchOperationAction;
import com.recording.platform.batch.model.BatchOperationJob;
import com.recording.platform.batch.model.BatchOperationSource;
import com.recording.platform.batch.service.BatchOperationCommand;
import com.recording.platform.batch.service.BatchOperationPreview;
import com.recording.platform.batch.service.BatchOperationSelection;
import com.recording.platform.batch.service.BatchOperationService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItemStatus;
import java.util.List;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/batch-operation-jobs")
public class BatchOperationController {
	private final BatchOperationService service;
	public BatchOperationController(BatchOperationService service) { this.service = service; }

	@PostMapping("/preview")
	public BatchOperationPreview preview(
		@Valid @RequestBody SelectionRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return service.preview(request.toSelection(), actor);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public BatchOperationJob create(
		@Valid @RequestBody CreateRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return service.create(new BatchOperationCommand(
			request.operationId(), request.action(), request.selection().toSelection(),
			request.targetStatus(), request.reviewerId()
		), actor);
	}

	@GetMapping("/{jobId}")
	public BatchOperationJob get(
		@PathVariable String jobId,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return service.get(jobId, actor);
	}

	@GetMapping
	public List<BatchOperationJob> recent(
		@RequestParam(required = false) String taskId,
		@RequestParam(required = false) BatchOperationSource source,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return service.recent(taskId, source, actor);
	}

	public record SelectionRequest(
		@NotBlank String taskId,
		@NotNull BatchOperationSource source,
		Set<@NotBlank String> excludedItemIds
	) {
		BatchOperationSelection toSelection() {
			return new BatchOperationSelection(taskId, source, excludedItemIds);
		}
	}
	public record CreateRequest(
		@NotBlank String operationId,
		@NotNull BatchOperationAction action,
		@NotNull @Valid SelectionRequest selection,
		TaskItemStatus targetStatus,
		String reviewerId
	) { }
}

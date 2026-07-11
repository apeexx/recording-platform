package com.recording.platform.task.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.CreateTaskCommand;
import com.recording.platform.task.service.TaskManagementService;
import com.recording.platform.task.service.TaskQueryService;
import com.recording.platform.task.service.TaskVersionSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
	private final TaskManagementService management;
	private final TaskQueryService queries;
	private final IdempotencyService idempotency;
	public TaskController(
		TaskManagementService management,
		TaskQueryService queries,
		IdempotencyService idempotency
	) {
		this.management = management;
		this.queries = queries;
		this.idempotency = idempotency;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskRecord create(
		@Valid @RequestBody CreateTaskRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "task:create", operationId, TaskRecord.class, () ->
			management.create(new CreateTaskCommand(
				request.taskCode(), request.platformId(), request.name(), request.description(), request.version().spec()
			))
		);
	}

	@PutMapping("/{taskId}")
	public TaskRecord update(
		@PathVariable String taskId,
		@Valid @RequestBody UpdateTaskRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "task:update:" + taskId, operationId, TaskRecord.class, () ->
			management.updateStructure(taskId, request.name(), request.description(), request.version().spec())
		);
	}

	@PostMapping("/{taskId}/publish") public TaskRecord publish(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) { return transition(authentication, "publish", taskId, operationId, () -> management.publish(taskId)); }
	@PostMapping("/{taskId}/pause") public TaskRecord pause(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) { return transition(authentication, "pause", taskId, operationId, () -> management.pause(taskId)); }
	@PostMapping("/{taskId}/resume") public TaskRecord resume(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) { return transition(authentication, "resume", taskId, operationId, () -> management.resume(taskId)); }
	@PostMapping("/{taskId}/end") public TaskRecord end(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) { return transition(authentication, "end", taskId, operationId, () -> management.end(taskId)); }

	@GetMapping
	public PageResponse<TaskQueryService.TaskView> list(
		@AuthenticationPrincipal PlatformPrincipal actor,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) { return queries.list(actor, page, size); }

	@GetMapping("/{taskId}")
	public TaskQueryService.TaskView get(@PathVariable String taskId, @AuthenticationPrincipal PlatformPrincipal actor) {
		return queries.get(taskId, actor);
	}

	private TaskRecord transition(
		Authentication authentication,
		String action,
		String taskId,
		String operationId,
		java.util.function.Supplier<TaskRecord> mutation
	) {
		return idempotency.execute(
			authentication, "task:" + action + ":" + taskId, operationId, TaskRecord.class, mutation
		);
	}

	public record CreateTaskRequest(
		@NotNull String taskCode,
		@NotNull String platformId,
		@NotNull String name,
		String description,
		@NotNull @Valid TaskVersionRequest version
	) { }
	public record UpdateTaskRequest(@NotNull String name, String description, @NotNull @Valid TaskVersionRequest version) { }
	public record TaskVersionRequest(
		@NotNull Set<ReferenceType> referenceTypes,
		Boolean fixedRecording,
		Boolean textInputEnabled,
		Boolean humanReviewEnabled,
		@NotNull RecordingFormat recordingFormat,
		@NotNull Set<Integer> sampleRates,
		Integer channels,
		Long minDurationMillis,
		Long maxDurationMillis,
		List<String> rejectionReasons,
		Boolean aiEnabled,
		String aiProvider,
		String aiModel
	) {
		TaskVersionSpec spec() {
			return new TaskVersionSpec(
				referenceTypes,
				Boolean.TRUE.equals(fixedRecording),
				Boolean.TRUE.equals(textInputEnabled),
				humanReviewEnabled == null || humanReviewEnabled,
				recordingFormat,
				sampleRates,
				channels == null ? 1 : channels,
				minDurationMillis == null ? 1000 : minDurationMillis,
				maxDurationMillis == null ? 600000 : maxDurationMillis,
				rejectionReasons == null ? List.of() : rejectionReasons,
				Boolean.TRUE.equals(aiEnabled),
				aiProvider,
				aiModel
			);
		}
	}
}

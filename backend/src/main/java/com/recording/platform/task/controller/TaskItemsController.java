package com.recording.platform.task.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.importing.AddTaskItemCommand;
import com.recording.platform.importing.TaskItemCreationService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.model.IdentityUser;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks/{taskId}/items")
public class TaskItemsController {
	private final TaskItemCreationService creation;
	private final TaskPoolService pool;
	private final TaskItemStore items;
	private final IdempotencyService idempotency;
	private final IdentityDirectory users;

	public TaskItemsController(
		TaskItemCreationService creation,
		TaskPoolService pool,
		TaskItemStore items,
		IdempotencyService idempotency,
		IdentityDirectory users
	) {
		this.creation = creation;
		this.pool = pool;
		this.items = items;
		this.idempotency = idempotency;
		this.users = users;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskItem add(
		@PathVariable String taskId,
		@Valid @RequestBody AddItemRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "item:add:" + taskId, operationId, TaskItem.class, () ->
			creation.add(taskId, request.command(), operationId, actor)
		);
	}

	@PostMapping("/start")
	public TaskItem start(
		@PathVariable String taskId,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication, "item:start:" + taskId, operationId, TaskItem.class,
			() -> pool.start(taskId, actor)
		);
	}

	@GetMapping
	public PageResponse<TaskItem> list(
		@PathVariable String taskId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		var result = items.findAllByTaskId(
			taskId,
			PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
		);
		Map<String, IdentityUser> identities = users.findAllByIdIn(
			result.getContent().stream()
				.flatMap(item -> java.util.stream.Stream.of(item.getCollectorId(), item.getReviewerId()))
				.filter(java.util.Objects::nonNull).distinct().toList()
		).stream().collect(Collectors.toMap(IdentityUser::id, Function.identity()));
		result.getContent().forEach(item -> {
			IdentityUser collector = identities.get(item.getCollectorId());
			IdentityUser reviewer = identities.get(item.getReviewerId());
			item.setCollectorName(collector == null ? null : collector.name());
			item.setReviewerName(reviewer == null ? null : reviewer.name());
		});
		return PageResponse.from(result);
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
}

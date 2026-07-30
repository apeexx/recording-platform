package com.recording.platform.task.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.importing.AddTaskItemCommand;
import com.recording.platform.importing.TaskItemCreationService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.report.service.ReportDateRange;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.service.TaskItemCsvExportService;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
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
import java.util.Set;
import com.recording.platform.task.service.AdminTaskItemGroup;
import com.recording.platform.task.service.TaskItemFilter;
import com.recording.platform.task.service.TaskItemResultKind;

@RestController
@RequestMapping("/api/tasks/{taskId}/items")
public class TaskItemsController {
	private final TaskItemCreationService creation;
	private final TaskPoolService pool;
	private final TaskItemStore items;
	private final IdempotencyService idempotency;
	private final IdentityDirectory users;
	private final TaskItemCsvExportService csvExport;

	public TaskItemsController(
		TaskItemCreationService creation,
		TaskPoolService pool,
		TaskItemStore items,
		IdempotencyService idempotency,
		IdentityDirectory users,
		TaskItemCsvExportService csvExport
	) {
		this.creation = creation;
		this.pool = pool;
		this.items = items;
		this.idempotency = idempotency;
		this.users = users;
		this.csvExport = csvExport;
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
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(name = "itemCode", required = false) Set<String> itemCodes,
		@RequestParam(defaultValue = "") String itemCodeQuery,
		@RequestParam(name = "group", required = false) Set<AdminTaskItemGroup> groups,
		@RequestParam(name = "collectorId", required = false) Set<String> collectorIds,
		@RequestParam(defaultValue = "false") boolean includeUnassigned,
		@RequestParam(name = "result", required = false) Set<TaskItemResultKind> results,
		@RequestParam(defaultValue = "") String sourceItemIdQuery
	) {
		var result = items.findAllByTaskId(
			taskId,
			new TaskItemFilter(
				itemCodes, itemCodeQuery, groups, collectorIds, includeUnassigned, results,
				sourceItemIdQuery, null, null
			),
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

	@GetMapping(value = "/export.csv", produces = "text/csv")
	public void export(
		@PathVariable String taskId,
		@RequestParam(name = "itemCode", required = false) Set<String> itemCodes,
		@RequestParam(defaultValue = "") String itemCodeQuery,
		@RequestParam(name = "group", required = false) Set<AdminTaskItemGroup> groups,
		@RequestParam(name = "collectorId", required = false) Set<String> collectorIds,
		@RequestParam(defaultValue = "false") boolean includeUnassigned,
		@RequestParam(name = "result", required = false) Set<TaskItemResultKind> results,
		@RequestParam(defaultValue = "") String sourceItemIdQuery,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate,
		HttpServletResponse response
	) throws IOException {
		ReportDateRange range = ReportDateRange.of(fromDate, toDate);
		TaskItemFilter filter = new TaskItemFilter(
			itemCodes, itemCodeQuery, groups, collectorIds, includeUnassigned, results,
			sourceItemIdQuery, range.fromInclusive(), range.toExclusive()
		);
		response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"task-items.csv\"");
		response.setHeader("Cache-Control", "no-store");
		csvExport.write(taskId, filter, response.getOutputStream());
	}

	@GetMapping("/export.csv/ready")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void prepareExport(
		@PathVariable String taskId,
		@RequestParam(required = false) LocalDate fromDate,
		@RequestParam(required = false) LocalDate toDate
	) {
		ReportDateRange.of(fromDate, toDate);
		csvExport.prepare(taskId);
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

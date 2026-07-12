package com.recording.platform.operation.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.operation.dto.OperationView;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.TaskItemStore;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.recording.platform.operation.store.OperationQueryStore;
import com.recording.platform.operation.store.OperationEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OperationService {
	private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
	private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final TaskItemStore items;
	private final OperationQueryStore queries;

	public OperationService(TaskItemStore items) { this(items, null); }

	@Autowired
	public OperationService(TaskItemStore items, OperationQueryStore queries) {
		this.items = items;
		this.queries = queries;
	}

	public PageResponse<OperationView> itemOperations(
		String itemId, int page, int size, PlatformPrincipal actor
	) {
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
		requireAccess(item, actor);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		List<OperationHistory> operations = new ArrayList<>(
			item.getOperations() == null ? List.of() : item.getOperations()
		);
		operations.sort(Comparator.comparing(OperationHistory::getOccurredAt,
			Comparator.nullsLast(Comparator.naturalOrder())).reversed());
		int from = Math.min(safePage * safeSize, operations.size());
		int to = Math.min(from + safeSize, operations.size());
		List<OperationView> views = operations.subList(from, to).stream().map(this::view).toList();
		return new PageResponse<>(views, safePage, safeSize, operations.size());
	}

	public PageResponse<OperationView> globalOperations(int page, int size, PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.REVIEWER) throw forbidden();
		if (queries == null) throw new IllegalStateException("operation query store required");
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		String actorFilter = actor.role() == UserRole.REVIEWER ? actor.userId() : null;
		var result = queries.findOperations(actorFilter, PageRequest.of(safePage, safeSize));
		return new PageResponse<>(result.getContent().stream().map(this::view).toList(),
			result.getNumber(), result.getSize(), result.getTotalElements());
	}

	private OperationView view(OperationHistory operation) {
		String time = operation.getOccurredAt() == null ? null
			: DISPLAY_TIME.format(operation.getOccurredAt().atZone(SHANGHAI));
		return new OperationView(time, operation.getActorUsername(), operation.getContent());
	}

	private OperationView view(OperationEntry operation) {
		String time = operation.occurredAt() == null ? null
			: DISPLAY_TIME.format(operation.occurredAt().atZone(SHANGHAI));
		return new OperationView(time, operation.actorUsername(), operation.content());
	}

	private void requireAccess(TaskItem item, PlatformPrincipal actor) {
		if (actor == null) throw forbidden();
		if (actor.role() == UserRole.ADMIN) return;
		if (actor.role() == UserRole.COLLECTOR && actor.userId().equals(item.getCollectorId())) return;
		if (actor.role() == UserRole.REVIEWER && (actor.userId().equals(item.getReviewerId())
			|| item.getOperations() != null && item.getOperations().stream()
				.anyMatch((operation) -> actor.userId().equals(operation.getActorUserId())))) return;
		throw forbidden();
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限查看该条目的操作记录");
	}
}

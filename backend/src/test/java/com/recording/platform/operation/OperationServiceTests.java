package com.recording.platform.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.operation.service.OperationService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.OperationHistory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.operation.store.OperationQueryStore;
import com.recording.platform.operation.store.OperationEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class OperationServiceTests {
	@Test
	void exposesShanghaiTimeOperatorAndContentWithoutLosingInternalMetadata() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item();
		OperationHistory operation = new OperationHistory();
		operation.setOperationId("operation-1");
		operation.setType("REVIEW_REJECT");
		operation.setActorUserId("reviewer-1");
		operation.setActorUsername("审核员一");
		operation.setContent("审核环节驳回到采集环节");
		operation.setOccurredAt(Instant.parse("2026-07-12T08:30:00Z"));
		item.setOperations(List.of(operation));
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		OperationService service = new OperationService(items);

		var page = service.itemOperations("item-1", 0, 20, admin());

		assertThat(page.items()).singleElement().satisfies((view) -> {
			assertThat(view.time()).isEqualTo("2026-07-12 16:30:00");
			assertThat(view.operator()).isEqualTo("审核员一");
			assertThat(view.content()).isEqualTo("审核环节驳回到采集环节");
		});
		assertThat(item.getOperations().get(0).getOperationId()).isEqualTo("operation-1");
	}

	@Test
	void collectorCanOnlyReadOwnItemOperations() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem item = item();
		item.setCollectorId("collector-2");
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		OperationService service = new OperationService(items);

		assertThatThrownBy(() -> service.itemOperations("item-1", 0, 20, collector("collector-1")))
			.isInstanceOfSatisfying(ApiException.class,
				(error) -> assertThat(error.getCode()).isEqualTo("ACCESS_DENIED"));
	}

	@Test
	void globalOperationsUseOperationLevelPaginationAndReviewerActorFilter() {
		TaskItemStore items = mock(TaskItemStore.class);
		OperationQueryStore queries = mock(OperationQueryStore.class);
		PageRequest pageable = PageRequest.of(1, 20);
		OperationEntry entry = new OperationEntry(
			"item-1", "reviewer-1", "审核员一", "审核环节提交", Instant.parse("2026-07-12T08:30:00Z")
		);
		when(queries.findOperations("reviewer-1", pageable))
			.thenReturn(new PageImpl<>(List.of(entry), pageable, 21));
		OperationService service = new OperationService(items, queries);
		PlatformPrincipal reviewer = new PlatformPrincipal(
			"s", "reviewer-1", "reviewer", "审核员一", UserRole.REVIEWER, SessionType.WEB, false
		);

		var result = service.globalOperations(1, 20, reviewer);

		assertThat(result.total()).isEqualTo(21);
		assertThat(result.items()).singleElement().satisfies((view) -> {
			assertThat(view.time()).isEqualTo("2026-07-12 16:30:00");
			assertThat(view.operator()).isEqualTo("审核员一");
		});
	}

	private TaskItem item() {
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setCollectorId("collector-1");
		return item;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	private PlatformPrincipal collector(String id) {
		return new PlatformPrincipal("s", id, null, "采集员", UserRole.COLLECTOR, SessionType.MINIPROGRAM, false);
	}
}

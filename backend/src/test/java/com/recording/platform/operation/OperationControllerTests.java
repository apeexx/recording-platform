package com.recording.platform.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.operation.controller.OperationController;
import com.recording.platform.operation.dto.OperationView;
import com.recording.platform.operation.service.OperationService;
import com.recording.platform.security.PlatformPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationControllerTests {
	@Test
	void exposesItemOperationPagination() {
		OperationService service = mock(OperationService.class);
		OperationController controller = new OperationController(service);
		PlatformPrincipal admin = admin();
		PageResponse<OperationView> response = new PageResponse<>(
			List.of(new OperationView("2026-07-12 16:30:00", "管理员", "调整状态")), 0, 20, 1
		);
		when(service.itemOperations("item-1", 0, 20, admin)).thenReturn(response);

		assertThat(controller.itemOperations("item-1", 0, 20, admin)).isSameAs(response);
	}

	@Test
	void exposesGlobalOperationPagination() {
		OperationService service = mock(OperationService.class);
		OperationController controller = new OperationController(service);
		PlatformPrincipal admin = admin();
		PageResponse<OperationView> response = new PageResponse<>(List.of(), 0, 20, 0);
		when(service.globalOperations(0, 20, admin)).thenReturn(response);

		assertThat(controller.globalOperations(0, 20, admin)).isSameAs(response);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}
}

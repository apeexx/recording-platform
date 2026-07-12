package com.recording.platform.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.report.controller.ReportController;
import com.recording.platform.report.dto.ReviewerSummary;
import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.security.PlatformPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportControllerTests {
	@Test
	void exposesTaskCollectorReviewerAndPersonalReports() {
		ReportService service = mock(ReportService.class);
		ReportController controller = new ReportController(service);
		PlatformPrincipal admin = admin();
		WorkSummary work = new WorkSummary(2, 3000, 1, 2000, 0, 0);
		ReviewerSummary review = new ReviewerSummary(1, 0, 1, 0, 120000);
		when(service.task("task-1", admin)).thenReturn(work);
		when(service.collector("collector-1", admin)).thenReturn(work);
		when(service.reviewer("reviewer-1", admin)).thenReturn(review);
		when(service.me(admin)).thenReturn(work);
		PageResponse<SubmissionView> page = new PageResponse<>(List.of(), 0, 20, 0);
		when(service.mySubmissions(0, 20, admin)).thenReturn(page);

		assertThat(controller.task("task-1", admin)).isSameAs(work);
		assertThat(controller.collector("collector-1", admin)).isSameAs(work);
		assertThat(controller.reviewer("reviewer-1", admin)).isSameAs(review);
		assertThat(controller.me(admin)).isSameAs(work);
		assertThat(controller.mySubmissions(0, 20, admin)).isSameAs(page);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal("s", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}
}

package com.recording.platform.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.review.controller.ReviewController;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewControllerTests {
	@Test
	void exposesClaimReleaseApproveAndRejectContracts() {
		ReviewService service = mock(ReviewService.class);
		ReviewController controller = new ReviewController(service);
		PlatformPrincipal reviewer = reviewer();
		TaskItem claimed = item("item-1", TaskItemStatus.REVIEW_PENDING);
		when(service.claim("task-1", "claim-1", reviewer)).thenReturn(claimed);
		when(service.release("item-1", "release-1", 2, reviewer)).thenReturn(claimed);
		TaskItem completed = item("item-1", TaskItemStatus.COMPLETED);
		when(service.approve("item-1", "approve-1", 3, "修订文本", reviewer)).thenReturn(completed);
		TaskItem rejected = item("item-1", TaskItemStatus.REWORK_PENDING);
		when(service.reject("item-1", "reject-1", 4, List.of("空音频"), "重录", reviewer))
			.thenReturn(rejected);

		assertThat(controller.claim("task-1", "claim-1", reviewer)).isSameAs(claimed);
		assertThat(controller.release("item-1", new ReviewController.ItemOperationRequest("release-1", 2L), reviewer))
			.isSameAs(claimed);
		assertThat(controller.approve("item-1", new ReviewController.ApproveRequest("approve-1", 3L, "修订文本"), reviewer))
			.isSameAs(completed);
		assertThat(controller.reject("item-1", new ReviewController.RejectRequest(
			"reject-1", 4L, List.of("空音频"), "重录"
		), reviewer)).isSameAs(rejected);

		verify(service).claim("task-1", "claim-1", reviewer);
		verify(service).release("item-1", "release-1", 2, reviewer);
		verify(service).approve("item-1", "approve-1", 3, "修订文本", reviewer);
		verify(service).reject("item-1", "reject-1", 4, List.of("空音频"), "重录", reviewer);
	}

	private TaskItem item(String id, TaskItemStatus status) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setStatus(status);
		return item;
	}

	private PlatformPrincipal reviewer() {
		return new PlatformPrincipal(
			"session-1", "reviewer-1", "reviewer", "审核员一",
			UserRole.REVIEWER, SessionType.WEB, false
		);
	}
}

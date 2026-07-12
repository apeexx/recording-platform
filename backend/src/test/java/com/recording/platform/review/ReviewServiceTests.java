package com.recording.platform.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskVersionStore;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.review.service.BatchReviewCommand;
import com.recording.platform.review.service.BatchReviewResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ReviewServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-12T08:00:00Z"), ZoneOffset.UTC);

	@Test
	void reviewerClaimsOnePendingItemWithAReviewAssignment() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem claimed = pending("item-1", 4);
		claimed.setReviewerId("reviewer-1");
		claimed.setReviewAssignmentId("review-assignment-1");
		claimed.setRevision(5);
		when(items.claimReview(any())).thenReturn(Optional.of(claimed));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		TaskItem result = service.claim("operation-1", reviewer());

		assertThat(result.getReviewerId()).isEqualTo("reviewer-1");
		assertThat(result.getReviewAssignmentId()).isNotBlank();
		assertThat(result.getRevision()).isEqualTo(5);
		verify(items).claimReview(any(ReviewClaimMutation.class));
	}

	@Test
	void secondReviewerGetsConflictWhenAtomicClaimFindsNoItem() {
		TaskItemStore items = mock(TaskItemStore.class);
		when(items.claimReview(any())).thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		assertThatThrownBy(() -> service.claim("operation-2", reviewer()))
			.isInstanceOfSatisfying(ApiException.class,
				(error) -> assertThat(error.getCode()).isEqualTo("NO_REVIEW_ITEM"));
	}

	@Test
	void reviewerReleasesOwnAssignmentWithoutClearingCollectedResult() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem existing = pending("item-1", 7);
		existing.setReviewerId("reviewer-1");
		existing.setReviewAssignmentId("review-assignment-1");
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		TaskItem released = pending("item-1", 8);
		when(items.releaseReviewIfCurrent(any())).thenReturn(Optional.of(released));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		TaskItem result = service.release("item-1", "operation-release", 7, reviewer());

		assertThat(result.getReviewerId()).isNull();
		assertThat(result.getReviewAssignmentId()).isNull();
		assertThat(result.getCurrentResult()).isNotNull();
		verify(items).releaseReviewIfCurrent(any(ReviewReleaseMutation.class));
	}

	@Test
	void reviewerApprovesAndMayReplaceTheCollectedText() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		TaskItem approved = assigned("item-1", 8);
		approved.setStatus(TaskItemStatus.COMPLETED);
		approved.setReviewerId(null);
		approved.setReviewAssignmentId(null);
		approved.setCurrentResult(new TaskItemResult(null, "审核修订文本"));
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(approved));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		TaskItem result = service.approve(
			"item-1", "operation-approve", 7, "审核修订文本", reviewer()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
		assertThat(result.getCurrentResult().text()).isEqualTo("审核修订文本");
		verify(items).decideReviewIfCurrent(any(ReviewDecisionMutation.class));
	}

	@Test
	void reviewerRejectsToOriginalCollectorWithConfiguredReasonsAndNote() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		TaskItem rejected = assigned("item-1", 8);
		rejected.setStatus(TaskItemStatus.RECORDING_PENDING);
		rejected.setReviewerId(null);
		rejected.setReviewAssignmentId(null);
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(rejected));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		TaskItem result = service.reject(
			"item-1", "operation-reject", 7, List.of("空音频", "内容不符"), "请重新录制", reviewer()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		assertThat(result.getCollectorId()).isEqualTo("collector-1");
		assertThat(result.getAssignmentId()).isEqualTo("collector-assignment-1");
	}

	@Test
	void rejectionRequiresConfiguredReasonOrAFreeformNote() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		assertThatThrownBy(() -> service.reject(
			"item-1", "operation-reject", 7, List.of(), " ", reviewer()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("REJECTION_REASON_REQUIRED"));
	}

	@Test
	void reviewerListsUnassignedPendingPoolAndClaimsABatch() {
		TaskItemStore items = mock(TaskItemStore.class);
		PageRequest page = PageRequest.of(0, 20);
		TaskItem available = pending("item-1", 1);
		when(items.findReviewPool(page)).thenReturn(new PageImpl<>(List.of(available), page, 1));
		TaskItem first = assigned("item-1", 2);
		TaskItem second = assigned("item-2", 2);
		when(items.claimReview(any()))
			.thenReturn(Optional.of(first))
			.thenReturn(Optional.of(second));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		assertThat(service.pool(page, reviewer()).getContent()).containsExactly(available);
		assertThat(service.claimBatch(2, "batch-operation", reviewer())).containsExactly(first, second);
	}

	@Test
	void batchClaimReturnsAlreadyClaimedItemsWhenPoolRunsOut() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = assigned("item-1", 2);
		when(items.claimReview(any())).thenReturn(Optional.of(first)).thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		assertThat(service.claimBatch(3, "batch-operation", reviewer())).containsExactly(first);
	}

	@Test
	void adminAssignsAPendingItemToAnActiveReviewer() {
		TaskItemStore items = mock(TaskItemStore.class);
		UserStore users = mock(UserStore.class);
		TaskItem existing = pending("item-1", 3);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		UserAccount reviewerAccount = new UserAccount();
		reviewerAccount.setId("reviewer-2");
		reviewerAccount.setRole(UserRole.REVIEWER);
		reviewerAccount.setStatus(UserStatus.ACTIVE);
		reviewerAccount.setName("审核员二");
		when(users.findById("reviewer-2")).thenReturn(Optional.of(reviewerAccount));
		TaskItem assigned = pending("item-1", 4);
		assigned.setReviewerId("reviewer-2");
		assigned.setReviewAssignmentId("review-assignment-2");
		when(items.assignReviewIfCurrent(any())).thenReturn(Optional.of(assigned));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), users, CLOCK);

		TaskItem result = service.assign("item-1", "reviewer-2", "assign-operation", 3, admin());

		assertThat(result.getReviewerId()).isEqualTo("reviewer-2");
		verify(items).assignReviewIfCurrent(any(ReviewAssignMutation.class));
	}

	@Test
	void adminCannotAssignDisabledOrNonReviewerAccount() {
		TaskItemStore items = mock(TaskItemStore.class);
		UserStore users = mock(UserStore.class);
		UserAccount disabled = new UserAccount();
		disabled.setId("reviewer-disabled");
		disabled.setRole(UserRole.REVIEWER);
		disabled.setStatus(UserStatus.DISABLED);
		when(users.findById("reviewer-disabled")).thenReturn(Optional.of(disabled));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), users, CLOCK);

		assertThatThrownBy(() -> service.assign(
			"item-1", "reviewer-disabled", "assign-operation", 3, admin()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("INVALID_REVIEWER"));
	}

	@Test
	void adminBatchApproveReturnsPerItemSuccessAndConflict() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = pending("item-1", 3);
		first.setTaskVersionId("version-1");
		TaskItem second = pending("item-2", 5);
		second.setTaskVersionId("version-1");
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem completed = pending("item-1", 4);
		completed.setStatus(TaskItemStatus.COMPLETED);
		when(items.adminApproveReviewIfCurrent(any()))
			.thenReturn(Optional.of(completed))
			.thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), mock(UserStore.class), CLOCK);

		List<BatchReviewResult> results = service.batchApprove(
			"batch-approve",
			List.of(new BatchReviewCommand("item-1", 3, null), new BatchReviewCommand("item-2", 5, null)),
			admin()
		);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(0).revision()).isEqualTo(4);
		assertThat(results.get(1).success()).isFalse();
		assertThat(results.get(1).code()).isEqualTo("STALE_STATE");
		verify(items, org.mockito.Mockito.times(2)).adminApproveReviewIfCurrent(any(AdminReviewApproveMutation.class));
	}

	@Test
	void reviewerCannotBatchApprove() {
		ReviewService service = new ReviewService(
			mock(TaskItemStore.class), mock(TaskVersionStore.class), mock(UserStore.class), CLOCK
		);

		assertThatThrownBy(() -> service.batchApprove(
			"batch-approve", List.of(new BatchReviewCommand("item-1", 3, null)), reviewer()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("ACCESS_DENIED"));
	}

	private TaskItem pending(String id, long revision) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setStatus(TaskItemStatus.REVIEW_PENDING);
		item.setRevision(revision);
		item.setCollectorId("collector-1");
		item.setAssignmentId("collector-assignment-1");
		item.setCurrentResult(new TaskItemResult(null, "普通话文本"));
		return item;
	}

	private TaskItem assigned(String id, long revision) {
		TaskItem item = pending(id, revision);
		item.setTaskVersionId("version-1");
		item.setReviewerId("reviewer-1");
		item.setReviewAssignmentId("review-assignment-1");
		return item;
	}

	private TaskVersion reviewVersion() {
		TaskVersion version = new TaskVersion();
		version.setId("version-1");
		version.setHumanReviewEnabled(true);
		version.setTextInputEnabled(true);
		version.setRejectionReasons(List.of("空音频", "内容不符"));
		return version;
	}

	private PlatformPrincipal reviewer() {
		return new PlatformPrincipal(
			"session-1", "reviewer-1", "reviewer", "审核员一",
			UserRole.REVIEWER, SessionType.WEB, false
		);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-admin", "admin-1", "admin", "管理员",
			UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

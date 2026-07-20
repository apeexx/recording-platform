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
import com.recording.platform.task.model.TaskResultType;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.task.store.ReviewAssignMutation;
import com.recording.platform.task.store.AdminReviewApproveMutation;
import com.recording.platform.task.store.AdminReviewDecisionMutation;
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

		TaskItem result = service.claim("task-1", "operation-1", reviewer());

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

		assertThatThrownBy(() -> service.claim("task-1", "operation-2", reviewer()))
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
		approved.setCurrentResult(new TaskItemResult(recording(), "审核修订文本"));
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(approved));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		TaskItem result = service.approve(
			"item-1", "operation-approve", 7, "审核修订文本", reviewer()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
		assertThat(result.getCurrentResult().text()).isEqualTo("审核修订文本");
		assertThat(result.getCurrentResult().audio()).isNotNull();
		verify(items).decideReviewIfCurrent(any(ReviewDecisionMutation.class));
	}

	@Test
	void audioResultCannotBeApprovedWithText() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = assigned("item-audio", 3);
		existing.setCurrentResult(new TaskItemResult(
			new com.recording.platform.task.model.SubmittedRecording(
				"media-1", "T000001/T000001-0000001.wav",
				com.recording.platform.task.model.RecordingFormat.WAV, 3200L, 16000, 1, 1200L
			), null
		));
		when(items.findById("item-audio")).thenReturn(Optional.of(existing));
		TaskVersion version = reviewVersion();
		version.setResultType(TaskResultType.AUDIO);
		when(versions.findById("version-1")).thenReturn(Optional.of(version));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		assertThatThrownBy(() -> service.approve(
			"item-audio", "approve-audio", 3, "不应出现的文字", reviewer()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("TEXT_NOT_ALLOWED"));
	}

	@Test
	void reviewerRejectsToOriginalCollectorWithConfiguredReasonsAndNote() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		TaskItem rejected = assigned("item-1", 8);
		rejected.setStatus(TaskItemStatus.REWORK_PENDING);
		rejected.setReviewerId(null);
		rejected.setReviewAssignmentId(null);
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(rejected));
		ReviewService service = new ReviewService(items, versions, CLOCK);

		TaskItem result = service.reject(
			"item-1", "operation-reject", 7, List.of("空音频", "内容不符"), "请重新录制", reviewer()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.REWORK_PENDING);
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
		assertThat(service.claimBatch("task-1", 2, "batch-operation", reviewer())).containsExactly(first, second);
	}

	@Test
	void adminListsAllPendingItemsIncludingReviewerAssignments() {
		TaskItemStore items = mock(TaskItemStore.class);
		PageRequest page = PageRequest.of(0, 20);
		TaskItem assigned = assigned("item-assigned", 2);
		when(items.findAllReviewPending(page)).thenReturn(new PageImpl<>(List.of(assigned), page, 1));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		assertThat(service.pool(page, admin()).getContent()).containsExactly(assigned);
		verify(items).findAllReviewPending(page);
	}

	@Test
	void reviewerTaskPoolIncludesOwnAssignmentsAndCollectorIdentity() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		PageRequest page = PageRequest.of(0, 20);
		TaskItem own = assigned("item-own", 2);
		own.setTaskId("task-1");
		own.setItemCode("T000001-0000001");
		when(items.findReviewPoolByTaskId("task-1", false, "reviewer-1", page))
			.thenReturn(new PageImpl<>(List.of(own), page, 1));
		IdentityUser collector = new IdentityUser("collector-1",UserType.MINIPROGRAM,null,"采集员一",UserRole.COLLECTOR,UserStatus.ACTIVE,false,null,null);
		when(users.findAllByIdIn(List.of("collector-1"))).thenReturn(List.of(collector));
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), users, CLOCK);

		var result = service.pool("task-1", page, reviewer());

		assertThat(result.getContent()).singleElement().satisfies(view -> {
			assertThat(view.collectorName()).isEqualTo("采集员一");
		});
		verify(items).findReviewPoolByTaskId("task-1", false, "reviewer-1", page);
	}

	@Test
	void batchClaimReturnsAlreadyClaimedItemsWhenPoolRunsOut() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = assigned("item-1", 2);
		when(items.claimReview(any())).thenReturn(Optional.of(first)).thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(TaskVersionStore.class), CLOCK);

		assertThat(service.claimBatch("task-1", 3, "batch-operation", reviewer())).containsExactly(first);
	}

	@Test
	void adminAssignsAPendingItemToAnActiveReviewer() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		TaskItem existing = pending("item-1", 3);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		IdentityUser reviewerAccount = new IdentityUser("reviewer-2",UserType.WEB,"reviewer-2","审核员二",UserRole.REVIEWER,UserStatus.ACTIVE,false,null,null);
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
		IdentityDirectory users = mock(IdentityDirectory.class);
		IdentityUser disabled = new IdentityUser("reviewer-disabled",UserType.WEB,"reviewer-disabled","禁用审核员",UserRole.REVIEWER,UserStatus.DISABLED,false,null,null);
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
		TaskVersionStore versions = mock(TaskVersionStore.class);
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem completed = pending("item-1", 4);
		completed.setStatus(TaskItemStatus.COMPLETED);
		when(items.adminApproveReviewIfCurrent(any()))
			.thenReturn(Optional.of(completed))
			.thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, versions, mock(IdentityDirectory.class), CLOCK);

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
	void adminMayApproveOneUnassignedPendingItemFromTheReviewWorkbench() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = pending("item-admin", 6);
		existing.setTaskVersionId("version-1");
		when(items.findById("item-admin")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		TaskItem completed = pending("item-admin", 7);
		completed.setStatus(TaskItemStatus.COMPLETED);
		completed.setCurrentResult(new TaskItemResult(null, "管理员修订文本"));
		when(items.adminDecideReviewIfCurrent(any())).thenReturn(Optional.of(completed));
		ReviewService service = new ReviewService(items, versions, mock(IdentityDirectory.class), CLOCK);

		TaskItem result = service.approve(
			"item-admin", "admin-single-approve", 6, "管理员修订文本", admin()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
		assertThat(result.getCurrentResult().text()).isEqualTo("管理员修订文本");
		verify(items).adminDecideReviewIfCurrent(any(AdminReviewDecisionMutation.class));
	}

	@Test
	void adminRejectionReachesTheAtomicDecisionInsteadOfBeingDeniedByRole() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskVersionStore versions = mock(TaskVersionStore.class);
		TaskItem existing = pending("item-admin", 6);
		existing.setTaskVersionId("version-1");
		when(items.findById("item-admin")).thenReturn(Optional.of(existing));
		when(versions.findById("version-1")).thenReturn(Optional.of(reviewVersion()));
		TaskItem rejected = pending("item-admin", 7);
		rejected.setStatus(TaskItemStatus.RECORDING_PENDING);
		when(items.adminDecideReviewIfCurrent(any())).thenReturn(Optional.of(rejected));
		ReviewService service = new ReviewService(items, versions, mock(IdentityDirectory.class), CLOCK);

		TaskItem result = service.reject(
			"item-admin", "admin-single-reject", 6, List.of("空音频"), "返修", admin()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		verify(items).adminDecideReviewIfCurrent(any(AdminReviewDecisionMutation.class));
	}

	@Test
	void reviewerCannotBatchApprove() {
		ReviewService service = new ReviewService(
			mock(TaskItemStore.class), mock(TaskVersionStore.class), mock(IdentityDirectory.class), CLOCK
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
		item.setCurrentResult(new TaskItemResult(recording(), "普通话文本"));
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
		version.setResultType(com.recording.platform.task.model.TaskResultType.TEXT);
		version.setRejectionReasons(List.of("空音频", "内容不符"));
		return version;
	}

	private com.recording.platform.task.model.SubmittedRecording recording() {
		return new com.recording.platform.task.model.SubmittedRecording(
			"media-1", "T000001/T000001-0000001.wav",
			com.recording.platform.task.model.RecordingFormat.WAV, 3200L, 16000, 1, 1200L
		);
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

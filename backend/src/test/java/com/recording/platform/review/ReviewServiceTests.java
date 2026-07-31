package com.recording.platform.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.review.service.ReviewPoolFilter;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.ReviewClaimMutation;
import com.recording.platform.task.store.ReviewItemClaimMutation;
import com.recording.platform.task.store.ReviewReleaseMutation;
import com.recording.platform.task.store.ReviewDecisionMutation;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.ReviewTaskMetrics;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.model.TaskRecord;
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
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ReviewServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T19:00:00Z"), ZoneOffset.UTC);

	@Test
	void reviewTaskSummariesUseOneAggregateFilterBacklogAndSortStably() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskRecord first = task("task-1", "T000002", "任务二");
		TaskRecord second = task("task-2", "T000001", "任务一");
		TaskRecord empty = task("task-3", "T000003", "无积压");
		when(tasks.findAll(any())).thenReturn(new PageImpl<>(List.of(first, second, empty)));
		when(items.reviewTaskMetrics(any(), any(), any())).thenReturn(List.of(
			new ReviewTaskMetrics("task-1", 12, 5, 8, 6, 2, 1, 3),
			new ReviewTaskMetrics("task-2", 9, 4, 6, 3, 1, 0, 2),
			new ReviewTaskMetrics("task-3", 3, 3, 3, 3, 0, 0, 1)
		));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		var summaries = service.tasks(admin());

		assertThat(summaries).extracting("taskId").containsExactly("task-1", "task-2");
		assertThat(summaries.get(0).pendingCount()).isEqualTo(3);
		assertThat(summaries.get(0).submittedCount()).isEqualTo(2);
		assertThat(summaries.get(0).reviewPendingCount()).isEqualTo(1);
		assertThat(summaries.get(0).todayCompletedCount()).isEqualTo(3);
		verify(items).reviewTaskMetrics(
			eq(List.of("task-1", "task-2", "task-3")),
			eq(Instant.parse("2026-07-10T20:00:00Z")),
			eq(Instant.parse("2026-07-11T20:00:00Z"))
		);
	}

	@Test
	void reviewTaskSummariesCanIncludeClearedHumanReviewTasksWithoutIncludingAutomaticTasks() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskRecord backlog = task("task-1", "T000002", "有积压");
		TaskRecord cleared = task("task-2", "T000001", "已清空");
		TaskRecord automatic = task("task-3", "T000003", "免审核");
		automatic.getConfiguration().setHumanReviewEnabled(false);
		when(tasks.findAll(any())).thenReturn(new PageImpl<>(List.of(cleared, automatic, backlog)));
		when(items.reviewTaskMetrics(any(), any(), any())).thenReturn(List.of(
			new ReviewTaskMetrics("task-1", 12, 5, 8, 6, 2, 1, 3),
			new ReviewTaskMetrics("task-2", 9, 9, 9, 9, 0, 0, 4),
			new ReviewTaskMetrics("task-3", 7, 7, 7, 7, 0, 0, 5)
		));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		var summaries = service.tasks(admin(), true);

		assertThat(summaries).extracting("taskId").containsExactly("task-1", "task-2");
		assertThat(summaries.get(1).pendingCount()).isZero();
		assertThat(summaries.get(1).todayCompletedCount()).isEqualTo(4);
	}

	@Test
	void singleTaskSummaryReturnsMetricsAndMissingTaskUsesExistingNotFoundError() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskRecord task = task("task-1", "T000001", "任务一");
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(items.reviewTaskMetrics(any(), any(), any())).thenReturn(List.of(
			new ReviewTaskMetrics("task-1", 10, 4, 7, 5, 2, 1, 2)
		));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		assertThat(service.taskSummary("task-1", reviewer()).reviewProcessedCount()).isEqualTo(5);
		assertThatThrownBy(() -> service.taskSummary("missing", reviewer()))
			.isInstanceOfSatisfying(ApiException.class,
				error -> assertThat(error.getCode()).isEqualTo("TASK_NOT_FOUND"));
	}

	@Test
	void adminOrReviewerClaimsASpecificSubmittedItemBeforeDecision() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem submitted = submitted("item-1", 3);
		when(items.findById("item-1")).thenReturn(Optional.of(submitted));
		TaskItem claimed = assigned("item-1", 4);
		when(items.claimReviewItem(any())).thenReturn(Optional.of(claimed));
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

		TaskItem result = service.claimItem("item-1", "claim-item-1", 3, admin());

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.REVIEW_PENDING);
		verify(items).claimReviewItem(any(ReviewItemClaimMutation.class));
	}

	@Test
	void reviewerClaimsOnePendingItemWithAReviewAssignment() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem claimed = pending("item-1", 4);
		claimed.setReviewerId("reviewer-1");
		claimed.setReviewAssignmentId("review-assignment-1");
		claimed.setRevision(5);
		when(items.claimReview(any())).thenReturn(Optional.of(claimed));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

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
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

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
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

		TaskItem result = service.release("item-1", "operation-release", 7, reviewer());

		assertThat(result.getReviewerId()).isNull();
		assertThat(result.getReviewAssignmentId()).isNull();
		assertThat(result.getCurrentResult()).isNotNull();
		verify(items).releaseReviewIfCurrent(any(ReviewReleaseMutation.class));
	}

	@Test
	void reviewerApprovesWithIndependentFinalAnswerAndKeepsCollectedText() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-1", 7);
		existing.setFirstCompletedAt(Instant.parse("2026-07-01T02:00:00Z"));
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		TaskItem approved = assigned("item-1", 8);
		approved.setStatus(TaskItemStatus.COMPLETED);
		approved.setReviewerId(null);
		approved.setReviewAssignmentId(null);
		approved.setCurrentResult(existing.getCurrentResult());
		approved.setReviewFinalAnswer("审核修订文本");
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(approved));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		TaskItem result = service.approve(
			"item-1", "operation-approve", 7, "审核修订文本", reviewer()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.COMPLETED);
		assertThat(result.getCurrentResult().text()).isEqualTo("普通话文本");
		assertThat(result.getCurrentResult().audio()).isNotNull();
		assertThat(result.getReviewFinalAnswer()).isEqualTo("审核修订文本");
		org.mockito.ArgumentCaptor<ReviewDecisionMutation> mutation =
			org.mockito.ArgumentCaptor.forClass(ReviewDecisionMutation.class);
		verify(items).decideReviewIfCurrent(mutation.capture());
		assertThat(mutation.getValue().firstCompletedAt())
			.isEqualTo(Instant.parse("2026-07-01T02:00:00Z"));
	}

	@Test
	void audioResultMayKeepAnOptionalFinalAnswer() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-audio", 3);
		existing.setCurrentResult(new TaskItemResult(
			new com.recording.platform.task.model.SubmittedRecording(
				"media-1", "T000001/T000001-0000001.wav",
				com.recording.platform.task.model.RecordingFormat.WAV, 3200L, 16000, 1, 1200L
			), null
		));
		when(items.findById("item-audio")).thenReturn(Optional.of(existing));
		TaskConfiguration configuration = reviewVersion();
		configuration.setResultType(TaskResultType.AUDIO);
		stubTask(tasks, configuration);
		TaskItem approved = assigned("item-audio", 4);
		approved.setStatus(TaskItemStatus.COMPLETED);
		approved.setCurrentResult(existing.getCurrentResult());
		approved.setReviewFinalAnswer("音频转写文本");
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(approved));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		TaskItem result = service.approve(
			"item-audio", "approve-audio", 3, "音频转写文本", reviewer()
		);

		assertThat(result.getCurrentResult()).isSameAs(existing.getCurrentResult());
		assertThat(result.getReviewFinalAnswer()).isEqualTo("音频转写文本");
	}

	@Test
	void textResultRequiresFinalAnswerEvenWhenOriginalOnlyContainsAudio() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-text", 3);
		existing.setCurrentResult(new TaskItemResult(recording(), null));
		when(items.findById("item-text")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		ReviewService service = new ReviewService(items, tasks, CLOCK);

		assertThatThrownBy(() -> service.approve(
			"item-text", "approve-text", 3, null, reviewer()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("REVIEW_FINAL_ANSWER_REQUIRED"));
	}

	@Test
	void reviewerRejectsToOriginalCollectorWithConfiguredReasonsAndNote() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		TaskItem rejected = assigned("item-1", 8);
		rejected.setStatus(TaskItemStatus.REWORK_PENDING);
		rejected.setReviewerId(null);
		rejected.setReviewAssignmentId(null);
		when(items.decideReviewIfCurrent(any())).thenReturn(Optional.of(rejected));
		ReviewService service = new ReviewService(items, tasks, CLOCK);

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
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-1", 7);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		ReviewService service = new ReviewService(items, tasks, CLOCK);

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
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

		assertThat(service.pool(page, reviewer()).getContent()).containsExactly(available);
		assertThat(service.claimBatch("task-1", 2, "batch-operation", reviewer())).containsExactly(first, second);
	}

	@Test
	void adminListsAllPendingItemsIncludingReviewerAssignments() {
		TaskItemStore items = mock(TaskItemStore.class);
		PageRequest page = PageRequest.of(0, 20);
		TaskItem assigned = assigned("item-assigned", 2);
		when(items.findAllReviewPending(page)).thenReturn(new PageImpl<>(List.of(assigned), page, 1));
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

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
		when(items.findReviewPoolByTaskId(
			eq("task-1"), eq(false), eq("reviewer-1"), any(ReviewPoolFilter.class), eq(page)
		))
			.thenReturn(new PageImpl<>(List.of(own), page, 1));
		IdentityUser collector = new IdentityUser("collector-1",UserType.MINIPROGRAM,null,"采集员一",UserRole.COLLECTOR,UserStatus.ACTIVE,false,null,null);
		when(users.findAllByIdIn(any())).thenReturn(List.of(collector));
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

		var result = service.pool("task-1", page, reviewer());

		assertThat(result.getContent()).singleElement().satisfies(view -> {
			assertThat(view.collectorName()).isEqualTo("采集员一");
		});
		verify(items).findReviewPoolByTaskId(
			eq("task-1"), eq(false), eq("reviewer-1"), any(ReviewPoolFilter.class), eq(page)
		);
	}

	@Test
	void reviewerFilterUsersAreDerivedOnlyFromRowsVisibleInTheReviewPool() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		PageRequest page = PageRequest.of(0, 200);
		TaskItem own = assigned("item-own", 2);
		own.setTaskId("task-1");
		when(items.findReviewPoolByTaskId(
			eq("task-1"), eq(false), eq("reviewer-1"), any(ReviewPoolFilter.class), eq(page)
		)).thenReturn(new PageImpl<>(List.of(own), page, 1));
		IdentityUser collector = new IdentityUser(
			"collector-1", UserType.MINIPROGRAM, "collector-account", "采集员一",
			UserRole.COLLECTOR, UserStatus.ACTIVE, false, null, null
		);
		when(users.findAllByIdIn(List.of("collector-1"))).thenReturn(List.of(collector));
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

		var result = service.filterUsers("task-1", UserRole.COLLECTOR, "采集员", reviewer());

		assertThat(result).singleElement().satisfies(user -> {
			assertThat(user.id()).isEqualTo("collector-1");
			assertThat(user.loginName()).isEqualTo("collector-account");
		});
		verify(items).findReviewPoolByTaskId(
			eq("task-1"), eq(false), eq("reviewer-1"), any(ReviewPoolFilter.class), eq(page)
		);
	}

	@Test
	void batchClaimReturnsAlreadyClaimedItemsWhenPoolRunsOut() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = assigned("item-1", 2);
		when(items.claimReview(any())).thenReturn(Optional.of(first)).thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(TaskStore.class), CLOCK);

		assertThat(service.claimBatch("task-1", 3, "batch-operation", reviewer())).containsExactly(first);
	}

	@Test
	void adminAssignsAPendingItemToAnActiveReviewer() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		TaskItem existing = submitted("item-1", 3);
		when(items.findById("item-1")).thenReturn(Optional.of(existing));
		IdentityUser reviewerAccount = new IdentityUser("reviewer-2",UserType.WEB,"reviewer-2","审核员二",UserRole.REVIEWER,UserStatus.ACTIVE,false,null,null);
		when(users.findById("reviewer-2")).thenReturn(Optional.of(reviewerAccount));
		TaskItem assigned = pending("item-1", 4);
		assigned.setReviewerId("reviewer-2");
		assigned.setReviewAssignmentId("review-assignment-2");
		when(items.assignReviewIfCurrent(any())).thenReturn(Optional.of(assigned));
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

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
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

		assertThatThrownBy(() -> service.assign(
			"item-1", "reviewer-disabled", "assign-operation", 3, admin()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("INVALID_REVIEWER"));
	}

	@Test
	void adminBatchApproveReturnsPerItemSuccessAndConflict() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = assigned("item-1", 3);
		TaskItem second = assigned("item-2", 5);
		TaskStore tasks = mock(TaskStore.class);
		stubTask(tasks, reviewVersion());
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem completed = pending("item-1", 4);
		completed.setStatus(TaskItemStatus.COMPLETED);
		when(items.adminApproveReviewIfCurrent(any()))
			.thenReturn(Optional.of(completed))
			.thenReturn(Optional.empty());
		ReviewService service = new ReviewService(items, mock(IdentityDirectory.class), tasks, CLOCK);

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
	void adminBatchClaimsSelectedSubmittedItemsAndReturnsPerItemConflicts() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskItem first = submitted("item-1", 3);
		TaskItem second = submitted("item-2", 5);
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		when(items.findById("item-2")).thenReturn(Optional.of(second));
		TaskItem claimed = assigned("item-1", 4);
		when(items.claimReviewItem(any())).thenReturn(Optional.of(claimed), Optional.empty());
		ReviewService service = new ReviewService(items, mock(IdentityDirectory.class), mock(TaskStore.class), CLOCK);

		var results = service.batchClaim(
			"batch-claim",
			List.of(new BatchReviewCommand("item-1", 3, null), new BatchReviewCommand("item-2", 5, null)),
			admin()
		);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(1).code()).isEqualTo("STALE_STATE");
	}

	@Test
	void adminBatchAssignsSelectedSubmittedItemsToOneActiveReviewer() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		IdentityUser target = new IdentityUser(
			"reviewer-2", UserType.WEB, "reviewer-2", "审核员二",
			UserRole.REVIEWER, UserStatus.ACTIVE, false, null, null
		);
		when(users.findById("reviewer-2")).thenReturn(Optional.of(target));
		TaskItem first = submitted("item-1", 3);
		when(items.findById("item-1")).thenReturn(Optional.of(first));
		TaskItem assigned = assigned("item-1", 4);
		assigned.setReviewerId("reviewer-2");
		when(items.assignReviewIfCurrent(any())).thenReturn(Optional.of(assigned));
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

		var results = service.batchAssign(
			"batch-assign", "reviewer-2", List.of(new BatchReviewCommand("item-1", 3, null)), admin()
		);

		assertThat(results).singleElement().satisfies(result -> assertThat(result.success()).isTrue());
	}

	@Test
	void reviewPoolReturnsReviewerNameSeparatelyFromReviewerId() {
		TaskItemStore items = mock(TaskItemStore.class);
		IdentityDirectory users = mock(IdentityDirectory.class);
		PageRequest page = PageRequest.of(0, 20);
		TaskItem row = assigned("item-1", 2);
		when(items.findReviewPoolByTaskId(
			eq("task-1"), eq(true), eq(null), any(ReviewPoolFilter.class), eq(page)
		))
			.thenReturn(new PageImpl<>(List.of(row), page, 1));
		IdentityUser collector = new IdentityUser(
			"collector-1", UserType.MINIPROGRAM, null, "采集员一",
			UserRole.COLLECTOR, UserStatus.ACTIVE, false, null, null
		);
		IdentityUser reviewer = new IdentityUser(
			"reviewer-1", UserType.WEB, "reviewer", "审核员一",
			UserRole.REVIEWER, UserStatus.ACTIVE, false, null, null
		);
		when(users.findAllByIdIn(any())).thenReturn(List.of(collector, reviewer));
		ReviewService service = new ReviewService(items, users, mock(TaskStore.class), CLOCK);

		var result = service.pool("task-1", page, admin());

		assertThat(result.getContent()).singleElement().satisfies(view -> {
			assertThat(view.collectorName()).isEqualTo("采集员一");
			assertThat(view.reviewerName()).isEqualTo("审核员一");
		});
	}

	@Test
	void adminMustClaimOrAssignBeforeApprovingFromTheReviewWorkbench() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = pending("item-admin", 6);
		when(items.findById("item-admin")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		ReviewService service = new ReviewService(items, mock(IdentityDirectory.class), tasks, CLOCK);

		assertThatThrownBy(() -> service.approve(
			"item-admin", "admin-single-approve", 6, "管理员修订文本", admin()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("STALE_STATE"));
		verify(items, org.mockito.Mockito.never()).adminDecideReviewIfCurrent(any());
	}

	@Test
	void adminRejectionReachesTheAtomicDecisionInsteadOfBeingDeniedByRole() {
		TaskItemStore items = mock(TaskItemStore.class);
		TaskStore tasks = mock(TaskStore.class);
		TaskItem existing = assigned("item-admin", 6);
		when(items.findById("item-admin")).thenReturn(Optional.of(existing));
		stubTask(tasks, reviewVersion());
		TaskItem rejected = pending("item-admin", 7);
		rejected.setStatus(TaskItemStatus.RECORDING_PENDING);
		when(items.adminDecideReviewIfCurrent(any())).thenReturn(Optional.of(rejected));
		ReviewService service = new ReviewService(items, mock(IdentityDirectory.class), tasks, CLOCK);

		TaskItem result = service.reject(
			"item-admin", "admin-single-reject", 6, List.of("空音频"), "返修", admin()
		);

		assertThat(result.getStatus()).isEqualTo(TaskItemStatus.RECORDING_PENDING);
		verify(items).adminDecideReviewIfCurrent(any(AdminReviewDecisionMutation.class));
	}

	@Test
	void reviewerCannotBatchApprove() {
		ReviewService service = new ReviewService(
			mock(TaskItemStore.class), mock(IdentityDirectory.class), mock(TaskStore.class), CLOCK
		);

		assertThatThrownBy(() -> service.batchApprove(
			"batch-approve", List.of(new BatchReviewCommand("item-1", 3, null)), reviewer()
		)).isInstanceOfSatisfying(ApiException.class,
			(error) -> assertThat(error.getCode()).isEqualTo("ACCESS_DENIED"));
	}

	private TaskItem pending(String id, long revision) {
		TaskItem item = new TaskItem();
		item.setId(id);
		item.setTaskId("task-1");
		item.setStatus(TaskItemStatus.REVIEW_PENDING);
		item.setRevision(revision);
		item.setCollectorId("collector-1");
		item.setAssignmentId("collector-assignment-1");
		item.setCurrentResult(new TaskItemResult(recording(), "普通话文本"));
		return item;
	}

	private TaskItem submitted(String id, long revision) {
		TaskItem item = pending(id, revision);
		item.setStatus(TaskItemStatus.SUBMITTED);
		return item;
	}

	private TaskItem assigned(String id, long revision) {
		TaskItem item = pending(id, revision);
		item.setReviewerId("reviewer-1");
		item.setReviewAssignmentId("review-assignment-1");
		return item;
	}

	private TaskConfiguration reviewVersion() {
		TaskConfiguration configuration = new TaskConfiguration();
		configuration.setHumanReviewEnabled(true);
		configuration.setResultType(com.recording.platform.task.model.TaskResultType.TEXT);
		configuration.setRejectionReasons(List.of("空音频", "内容不符"));
		return configuration;
	}

	private void stubTask(TaskStore tasks, TaskConfiguration configuration) {
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setConfiguration(configuration);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
	}

	private TaskRecord task(String id, String code, String name) {
		TaskRecord task = new TaskRecord();
		task.setId(id);
		task.setTaskCode(code);
		task.setName(name);
		task.setConfiguration(reviewVersion());
		return task;
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

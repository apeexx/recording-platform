package com.recording.platform.task.store;

import com.recording.platform.task.model.TaskItem;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;

public interface TaskItemStore {
	TaskItem save(TaskItem item);
	Optional<TaskItem> findById(String id);
	default Optional<TaskItem> findByTaskIdAndCreationOperationId(String taskId, String operationId) {
		return Optional.empty();
	}
	Optional<TaskItem> claimAvailable(ClaimMutation mutation);
	default Optional<TaskItem> claimReview(ReviewClaimMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> claimReviewItem(ReviewItemClaimMutation mutation) { return Optional.empty(); }
	default long countReviewPendingByTaskId(String taskId) { return 0; }
	default Page<TaskItem> findReviewPoolByTaskId(String taskId, boolean includeAssigned, String reviewerId, Pageable pageable) {
		return Page.empty(pageable);
	}
	default Optional<TaskItem> releaseReviewIfCurrent(ReviewReleaseMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> decideReviewIfCurrent(ReviewDecisionMutation mutation) { return Optional.empty(); }
	default Page<TaskItem> findReviewPool(Pageable pageable) { return Page.empty(pageable); }
	default Page<TaskItem> findAllReviewPending(Pageable pageable) { return Page.empty(pageable); }
	default Optional<TaskItem> assignReviewIfCurrent(ReviewAssignMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> adminApproveReviewIfCurrent(AdminReviewApproveMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> adminDecideReviewIfCurrent(AdminReviewDecisionMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> adminTransitionIfCurrent(AdminItemTransitionMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> adminDiscardIfCurrent(AdminItemTransitionMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> adminRestoreIfCurrent(AdminItemTransitionMutation mutation) { return Optional.empty(); }
	default Optional<TaskItem> updateReferencesIfAvailable(UpdateTaskItemReferencesMutation mutation) {
		return Optional.empty();
	}
	default Optional<TaskItem> deleteAvailableIfCurrent(String itemId, long expectedRevision) {
		return Optional.empty();
	}
	Optional<TaskItem> submitIfCurrent(SubmitMutation mutation);
	Optional<TaskItem> rejectIfCurrent(RejectMutation mutation);
	Optional<TaskItem> releaseIfCurrent(ReleaseMutation mutation);
	Page<TaskItem> findAllByTaskId(String taskId, Pageable pageable);
	default Page<TaskItem> findAllByCollectorIdAndStatusIn(
		String collectorId, String taskId,
		Collection<com.recording.platform.task.model.TaskItemStatus> statuses, Pageable pageable
	) { return Page.empty(pageable); }
	default List<TaskItem> findForReport(String collectorId, String taskId) { return List.of(); }
}

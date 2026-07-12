package com.recording.platform.review.controller;

import com.recording.platform.review.service.ReviewService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import com.recording.platform.api.PageResponse;
import com.recording.platform.review.service.BatchReviewCommand;
import com.recording.platform.review.service.BatchReviewResult;
import com.recording.platform.idempotency.IdempotencyService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
	private final ReviewService reviews;
	private final IdempotencyService idempotency;

	public ReviewController(ReviewService reviews) { this(reviews, null); }

	@Autowired
	public ReviewController(ReviewService reviews, IdempotencyService idempotency) {
		this.reviews = reviews;
		this.idempotency = idempotency;
	}

	@PostMapping("/claim")
	public TaskItem claim(
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:claim", operationId, TaskItem.class, () -> reviews.claim(operationId, actor));
	}

	@GetMapping("/pool")
	public PageResponse<TaskItem> pool(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return PageResponse.from(reviews.pool(
			PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)), actor
		));
	}

	@PostMapping("/claim-batch")
	public List<TaskItem> claimBatch(
		@Valid @RequestBody ClaimBatchRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:claim-batch", request.operationId(), new TypeReference<List<TaskItem>>() { },
			() -> reviews.claimBatch(request.count(), request.operationId(), actor));
	}

	@PostMapping("/assign")
	public TaskItem assign(
		@Valid @RequestBody AssignRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:assign", request.operationId(), TaskItem.class, () -> reviews.assign(
			request.itemId(), request.reviewerId(), request.operationId(), request.expectedRevision(), actor));
	}

	@PostMapping("/batch/approve")
	public List<BatchReviewResult> batchApprove(
		@Valid @RequestBody BatchApproveRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:batch-approve", request.operationId(),
			new TypeReference<List<BatchReviewResult>>() { }, () -> reviews.batchApprove(
			request.operationId(),
			request.items().stream().map((item) ->
				new BatchReviewCommand(item.itemId(), item.expectedRevision(), item.text())
			).toList(),
			actor
		));
	}

	@PostMapping("/{itemId}/release")
	public TaskItem release(
		@PathVariable String itemId,
		@Valid @RequestBody ItemOperationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:release:" + itemId, request.operationId(), TaskItem.class,
			() -> reviews.release(itemId, request.operationId(), request.expectedRevision(), actor));
	}

	@PostMapping("/{itemId}/approve")
	public TaskItem approve(
		@PathVariable String itemId,
		@Valid @RequestBody ApproveRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:approve:" + itemId, request.operationId(), TaskItem.class,
			() -> reviews.approve(itemId, request.operationId(), request.expectedRevision(), request.text(), actor));
	}

	@PostMapping("/{itemId}/reject")
	public TaskItem reject(
		@PathVariable String itemId,
		@Valid @RequestBody RejectRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return execute("review:reject:" + itemId, request.operationId(), TaskItem.class, () -> reviews.reject(
			itemId, request.operationId(), request.expectedRevision(), request.reasons(), request.note(), actor
		));
	}

	private <T> T execute(String action, String key, Class<T> type, Supplier<T> mutation) {
		if (idempotency == null) return mutation.get();
		return idempotency.execute(SecurityContextHolder.getContext().getAuthentication(), action, key, type, mutation);
	}

	private <T> T execute(String action, String key, TypeReference<T> type, Supplier<T> mutation) {
		if (idempotency == null) return mutation.get();
		return idempotency.execute(SecurityContextHolder.getContext().getAuthentication(), action, key, type, mutation);
	}

	public record ItemOperationRequest(@NotBlank String operationId, @NotNull Long expectedRevision) { }
	public record ClaimBatchRequest(@NotBlank String operationId, @NotNull Integer count) { }
	public record AssignRequest(
		@NotBlank String itemId,
		@NotBlank String reviewerId,
		@NotBlank String operationId,
		@NotNull Long expectedRevision
	) { }
	public record BatchApproveItem(@NotBlank String itemId, @NotNull Long expectedRevision, String text) { }
	public record BatchApproveRequest(
		@NotBlank String operationId,
		@NotNull List<@Valid BatchApproveItem> items
	) { }
	public record ApproveRequest(@NotBlank String operationId, @NotNull Long expectedRevision, String text) { }
	public record RejectRequest(
		@NotBlank String operationId,
		@NotNull Long expectedRevision,
		List<String> reasons,
		String note
	) { }
}

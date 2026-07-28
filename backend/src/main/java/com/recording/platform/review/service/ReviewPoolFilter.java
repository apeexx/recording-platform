package com.recording.platform.review.service;

import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.TaskItemResultKind;
import java.util.Set;

public record ReviewPoolFilter(
	Set<String> itemCodes,
	String itemCodeQuery,
	Set<TaskItemStatus> statuses,
	Set<String> collectorIds,
	Set<String> reviewerIds,
	boolean includeUnassignedReviewer,
	Set<TaskItemResultKind> results
) {
	public ReviewPoolFilter {
		itemCodes = itemCodes == null ? Set.of() : Set.copyOf(itemCodes);
		itemCodeQuery = itemCodeQuery == null ? "" : itemCodeQuery.trim();
		statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
		if (statuses.stream().anyMatch(status ->
			status != TaskItemStatus.SUBMITTED && status != TaskItemStatus.REVIEW_PENDING
		)) {
			throw new com.recording.platform.api.ApiException(
				org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
				"REVIEW_STATUS_INVALID",
				"审核池状态只能是 SUBMITTED 或 REVIEW_PENDING"
			);
		}
		collectorIds = collectorIds == null ? Set.of() : Set.copyOf(collectorIds);
		reviewerIds = reviewerIds == null ? Set.of() : Set.copyOf(reviewerIds);
		results = results == null ? Set.of() : Set.copyOf(results);
	}

	public static ReviewPoolFilter all() {
		return new ReviewPoolFilter(Set.of(), "", Set.of(), Set.of(), Set.of(), false, Set.of());
	}
}

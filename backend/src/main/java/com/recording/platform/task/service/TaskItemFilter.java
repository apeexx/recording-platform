package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItem;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record TaskItemFilter(
	Set<String> itemCodes,
	String itemCodeQuery,
	Set<AdminTaskItemGroup> groups,
	Set<String> collectorIds,
	boolean includeUnassigned,
	Set<TaskItemResultKind> results,
	String sourceItemIdQuery,
	Instant firstSubmittedFrom,
	Instant firstSubmittedTo
) {
	public TaskItemFilter {
		itemCodes = cleanStrings(itemCodes);
		itemCodeQuery = itemCodeQuery == null ? "" : itemCodeQuery.trim();
		groups = groups == null ? Set.of() : groups.stream()
			.filter(group -> group != null && group != AdminTaskItemGroup.ALL)
			.collect(Collectors.toUnmodifiableSet());
		collectorIds = cleanStrings(collectorIds);
		results = results == null ? Set.of() : results.stream()
			.filter(result -> result != null && result != TaskItemResultKind.ALL)
			.collect(Collectors.toUnmodifiableSet());
		sourceItemIdQuery = sourceItemIdQuery == null ? "" : sourceItemIdQuery.trim();
	}

	public TaskItemFilter(
		Set<String> itemCodes,
		String itemCodeQuery,
		Set<AdminTaskItemGroup> groups,
		Set<String> collectorIds,
		boolean includeUnassigned,
		Set<TaskItemResultKind> results
	) {
		this(
			itemCodes, itemCodeQuery, groups, collectorIds, includeUnassigned, results,
			"", null, null
		);
	}

	public TaskItemFilter(
		AdminTaskItemGroup group,
		Set<String> collectorIds,
		boolean includeUnassigned,
		TaskItemResultKind result
	) {
		this(Set.of(), "", single(group, AdminTaskItemGroup.ALL), collectorIds,
			includeUnassigned, single(result, TaskItemResultKind.ALL), "", null, null);
	}

	public static TaskItemFilter all() {
		return new TaskItemFilter(
			Set.of(), "", Set.of(), Set.of(), false, Set.of(), "", null, null
		);
	}

	public boolean matchesCollector(TaskItem item) {
		if (collectorIds.isEmpty() && !includeUnassigned) return true;
		String collectorId = item == null ? null : item.getCollectorId();
		return collectorId == null ? includeUnassigned : collectorIds.contains(collectorId);
	}

	public AdminTaskItemGroup group() {
		return groups.size() == 1 ? groups.iterator().next() : AdminTaskItemGroup.ALL;
	}

	public TaskItemResultKind result() {
		return results.size() == 1 ? results.iterator().next() : TaskItemResultKind.ALL;
	}

	private static <T> Set<T> single(T value, T allValue) {
		return value == null || value == allValue ? Set.of() : Set.of(value);
	}

	private static Set<String> cleanStrings(Set<String> values) {
		if (values == null) return Set.of();
		return values.stream().filter(value -> value != null && !value.isBlank())
			.map(String::trim).collect(Collectors.toUnmodifiableSet());
	}
}

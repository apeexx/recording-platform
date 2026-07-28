package com.recording.platform.batch.service;

import com.recording.platform.batch.model.BatchOperationSource;
import java.util.Set;
import com.recording.platform.task.service.AdminTaskItemGroup;
import com.recording.platform.task.service.TaskItemResultKind;

public record BatchOperationSelection(
	String taskId,
	BatchOperationSource source,
	Set<String> excludedItemIds,
	AdminTaskItemGroup group,
	Set<String> collectorIds,
	boolean includeUnassigned,
	TaskItemResultKind result,
	Set<String> itemCodes,
	Set<AdminTaskItemGroup> groups,
	Set<TaskItemResultKind> results
) {
	public BatchOperationSelection {
		excludedItemIds = excludedItemIds == null ? Set.of() : Set.copyOf(excludedItemIds);
		group = group == null ? AdminTaskItemGroup.ALL : group;
		collectorIds = collectorIds == null ? Set.of() : Set.copyOf(collectorIds);
		result = result == null ? TaskItemResultKind.ALL : result;
		itemCodes = itemCodes == null ? Set.of() : Set.copyOf(itemCodes);
		groups = merge(groups, group, AdminTaskItemGroup.ALL);
		results = merge(results, result, TaskItemResultKind.ALL);
	}

	public BatchOperationSelection(String taskId, BatchOperationSource source, Set<String> excludedItemIds) {
		this(taskId, source, excludedItemIds, AdminTaskItemGroup.ALL, Set.of(), false,
			TaskItemResultKind.ALL, Set.of(), Set.of(), Set.of());
	}

	public BatchOperationSelection(
		String taskId, BatchOperationSource source, Set<String> excludedItemIds,
		AdminTaskItemGroup group, Set<String> collectorIds,
		boolean includeUnassigned, TaskItemResultKind result
	) {
		this(taskId, source, excludedItemIds, group, collectorIds, includeUnassigned,
			result, Set.of(), Set.of(), Set.of());
	}

	private static <T> Set<T> merge(Set<T> values, T legacy, T allValue) {
		java.util.HashSet<T> merged = new java.util.HashSet<>();
		if (values != null) values.stream()
			.filter(value -> value != null && value != allValue).forEach(merged::add);
		if (legacy != null && legacy != allValue) merged.add(legacy);
		return Set.copyOf(merged);
	}
}

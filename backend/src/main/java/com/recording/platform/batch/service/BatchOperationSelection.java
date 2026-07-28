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
	TaskItemResultKind result
) {
	public BatchOperationSelection {
		excludedItemIds = excludedItemIds == null ? Set.of() : Set.copyOf(excludedItemIds);
		group = group == null ? AdminTaskItemGroup.ALL : group;
		collectorIds = collectorIds == null ? Set.of() : Set.copyOf(collectorIds);
		result = result == null ? TaskItemResultKind.ALL : result;
	}

	public BatchOperationSelection(String taskId, BatchOperationSource source, Set<String> excludedItemIds) {
		this(taskId, source, excludedItemIds, AdminTaskItemGroup.ALL, Set.of(), false, TaskItemResultKind.ALL);
	}
}

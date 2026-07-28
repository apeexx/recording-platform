package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItem;
import java.util.Set;

public record TaskItemFilter(
	AdminTaskItemGroup group,
	Set<String> collectorIds,
	boolean includeUnassigned,
	TaskItemResultKind result
) {
	public TaskItemFilter {
		group = group == null ? AdminTaskItemGroup.ALL : group;
		collectorIds = collectorIds == null ? Set.of() : Set.copyOf(collectorIds);
		result = result == null ? TaskItemResultKind.ALL : result;
	}

	public static TaskItemFilter all() {
		return new TaskItemFilter(AdminTaskItemGroup.ALL, Set.of(), false, TaskItemResultKind.ALL);
	}

	public boolean matchesCollector(TaskItem item) {
		if (collectorIds.isEmpty() && !includeUnassigned) return true;
		String collectorId = item == null ? null : item.getCollectorId();
		return collectorId == null ? includeUnassigned : collectorIds.contains(collectorId);
	}
}

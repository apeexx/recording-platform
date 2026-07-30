package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItemStatus;
import java.time.Instant;

public final class FirstCompletionTimePolicy {
	private FirstCompletionTimePolicy() { }

	public static Instant resolve(Instant existing, TaskItemStatus target, Instant occurredAt) {
		if (existing != null) return existing;
		return target == TaskItemStatus.COMPLETED ? occurredAt : null;
	}
}

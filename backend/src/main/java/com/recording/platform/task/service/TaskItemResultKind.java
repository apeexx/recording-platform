package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;

public enum TaskItemResultKind {
	ALL,
	NONE,
	TEXT_ONLY,
	AUDIO_ONLY,
	TEXT_AND_AUDIO;

	public boolean matches(TaskItem item) {
		if (this == ALL) return true;
		TaskItemResult value = item == null ? null : item.getCurrentResult();
		boolean text = value != null && value.text() != null && !value.text().isBlank();
		boolean audio = value != null && value.audio() != null;
		return switch (this) {
			case NONE -> !text && !audio;
			case TEXT_ONLY -> text && !audio;
			case AUDIO_ONLY -> !text && audio;
			case TEXT_AND_AUDIO -> text && audio;
			case ALL -> true;
		};
	}
}

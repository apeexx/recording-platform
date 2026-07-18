package com.recording.platform.task.store;

public interface SequenceStore {
	long next(String key);
}

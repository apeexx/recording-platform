package com.recording.platform.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecordingRetirement {
	private final RecordingMediaStorage storage;
	private final Path originalPath;
	private final Path backupPath;
	private final String backupRelativePath;
	private boolean closed;

	RecordingRetirement(RecordingMediaStorage storage, Path originalPath, Path backupPath) {
		this.storage = storage;
		this.originalPath = originalPath;
		this.backupPath = backupPath;
		this.backupRelativePath = storage.relative(backupPath);
	}

	public synchronized String deferCleanup() {
		closed = true;
		return backupRelativePath;
	}

	public synchronized void rollback() {
		if (closed) return;
		if (backupPath != null && Files.exists(backupPath)) {
			try {
				storage.atomicMove(backupPath, originalPath);
			} catch (IOException exception) {
				throw new IllegalStateException("无法恢复待清理录音文件", exception);
			}
		}
		closed = true;
	}
}

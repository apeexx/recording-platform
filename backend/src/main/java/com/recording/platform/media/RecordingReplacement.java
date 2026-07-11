package com.recording.platform.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RecordingReplacement {
	private final RecordingMediaStorage storage;
	private final Path currentPath;
	private final Path previousPath;
	private final Path backupPath;
	private final String backupRelativePath;
	private boolean closed;

	RecordingReplacement(
		RecordingMediaStorage storage,
		Path currentPath,
		Path previousPath,
		Path backupPath
	) {
		this.storage = storage;
		this.currentPath = currentPath;
		this.previousPath = previousPath;
		this.backupPath = backupPath;
		this.backupRelativePath = storage.relative(backupPath);
	}

	public synchronized void complete() {
		if (closed) return;
		storage.deleteRequired(backupPath);
		closed = true;
	}

	public synchronized void rollback() {
		if (closed) return;
		storage.deleteQuietly(currentPath);
		if (backupPath != null && Files.exists(backupPath)) {
			try {
				storage.atomicMove(backupPath, previousPath);
			} catch (IOException exception) {
				throw new IllegalStateException("无法恢复原录音文件", exception);
			}
		}
		closed = true;
	}

	public synchronized String deferCleanup() {
		closed = true;
		return backupRelativePath;
	}
}

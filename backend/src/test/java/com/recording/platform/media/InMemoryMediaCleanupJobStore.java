package com.recording.platform.media;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryMediaCleanupJobStore implements MediaCleanupJobStore {
	private final Map<String, MediaCleanupJob> data = new LinkedHashMap<>();

	@Override
	public synchronized MediaCleanupJob save(MediaCleanupJob job) {
		MediaCleanupJob stored = copy(job);
		stored.setVersion(job.getVersion() == null ? 0L : job.getVersion() + 1L);
		data.put(stored.getId(), stored);
		return copy(stored);
	}

	@Override
	public synchronized Optional<MediaCleanupJob> findByItemIdAndOperationId(String itemId, String operationId) {
		return data.values().stream()
			.filter((job) -> itemId.equals(job.getItemId()) && operationId.equals(job.getOperationId()))
			.findFirst()
			.map(InMemoryMediaCleanupJobStore::copy);
	}

	@Override
	public synchronized List<MediaCleanupJob> findPending() {
		return data.values().stream()
			.filter((job) -> job.getStatus() == MediaCleanupStatus.PENDING)
			.map(InMemoryMediaCleanupJobStore::copy)
			.toList();
	}

	private static MediaCleanupJob copy(MediaCleanupJob source) {
		MediaCleanupJob copy = new MediaCleanupJob();
		copy.setId(source.getId());
		copy.setVersion(source.getVersion());
		copy.setItemId(source.getItemId());
		copy.setOperationId(source.getOperationId());
		copy.setRelativePaths(new ArrayList<>(source.getRelativePaths()));
		copy.setMediaAssetIds(new ArrayList<>(source.getMediaAssetIds()));
		copy.setStatus(source.getStatus());
		copy.setAttempt(source.getAttempt());
		copy.setLastErrorSummary(source.getLastErrorSummary());
		copy.setCreatedAt(source.getCreatedAt());
		copy.setUpdatedAt(source.getUpdatedAt());
		copy.setCompletedAt(source.getCompletedAt());
		return copy;
	}
}

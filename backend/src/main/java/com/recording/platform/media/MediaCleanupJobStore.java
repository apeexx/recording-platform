package com.recording.platform.media;

import java.util.List;
import java.util.Optional;

public interface MediaCleanupJobStore {
	MediaCleanupJob save(MediaCleanupJob job);
	Optional<MediaCleanupJob> findByItemIdAndOperationId(String itemId, String operationId);
	List<MediaCleanupJob> findPending();
}

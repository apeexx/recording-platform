package com.recording.platform.media;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MongoMediaCleanupJobStore implements MediaCleanupJobStore {
	private final SpringDataMediaCleanupJobRepository repository;

	public MongoMediaCleanupJobStore(SpringDataMediaCleanupJobRepository repository) {
		this.repository = repository;
	}

	@Override public MediaCleanupJob save(MediaCleanupJob job) { return repository.save(job); }
	@Override public Optional<MediaCleanupJob> findByItemIdAndOperationId(String itemId, String operationId) {
		return repository.findByItemIdAndOperationId(itemId, operationId);
	}
	@Override public List<MediaCleanupJob> findPending() {
		return repository.findAllByStatusOrderByCreatedAtAsc(MediaCleanupStatus.PENDING);
	}
}

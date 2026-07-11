package com.recording.platform.media;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMediaCleanupJobRepository extends MongoRepository<MediaCleanupJob, String> {
	Optional<MediaCleanupJob> findByItemIdAndOperationId(String itemId, String operationId);
	List<MediaCleanupJob> findAllByStatusOrderByCreatedAtAsc(MediaCleanupStatus status);
}

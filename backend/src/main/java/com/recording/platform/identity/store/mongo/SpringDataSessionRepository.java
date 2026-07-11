package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataSessionRepository extends MongoRepository<SessionRecord, String> {
	Optional<SessionRecord> findByTokenHash(String tokenHash);

	Optional<SessionRecord> findFirstByUserIdAndTypeAndStatus(
		String userId,
		SessionType type,
		SessionStatus status
	);

	List<SessionRecord> findAllByUserIdAndStatus(String userId, SessionStatus status);
}

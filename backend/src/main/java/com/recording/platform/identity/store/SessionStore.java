package com.recording.platform.identity.store;

import com.recording.platform.identity.model.SessionRecord;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface SessionStore {
	SessionRecord save(SessionRecord session);

	Optional<SessionRecord> findById(String id);

	Optional<SessionRecord> findByTokenHash(String tokenHash);

	default Optional<SessionRecord> findActiveWebByUserId(String userId) {
		return findActiveByUserIdAndType(userId, com.recording.platform.identity.model.SessionType.WEB);
	}

	default Optional<SessionRecord> findActiveByUserIdAndType(
		String userId,
		com.recording.platform.identity.model.SessionType type
	) {
		return findActiveByUserId(userId).stream().filter(session -> session.getType() == type).findFirst();
	}

	List<SessionRecord> findActiveByUserId(String userId);

	boolean transitionStatus(String sessionId, com.recording.platform.identity.model.SessionStatus expected, com.recording.platform.identity.model.SessionStatus target);

	long transitionAllActiveByUserId(String userId, com.recording.platform.identity.model.SessionStatus target);

	boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt);
}

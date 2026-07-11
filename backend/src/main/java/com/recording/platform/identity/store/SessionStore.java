package com.recording.platform.identity.store;

import com.recording.platform.identity.model.SessionRecord;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface SessionStore {
	SessionRecord save(SessionRecord session);

	Optional<SessionRecord> findById(String id);

	Optional<SessionRecord> findByTokenHash(String tokenHash);

	Optional<SessionRecord> findActiveWebByUserId(String userId);

	List<SessionRecord> findActiveByUserId(String userId);

	boolean transitionStatus(String sessionId, com.recording.platform.identity.model.SessionStatus expected, com.recording.platform.identity.model.SessionStatus target);

	long transitionAllActiveByUserId(String userId, com.recording.platform.identity.model.SessionStatus target);

	boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt);
}

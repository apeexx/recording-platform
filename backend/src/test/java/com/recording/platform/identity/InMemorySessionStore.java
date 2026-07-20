package com.recording.platform.identity;

import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.store.SessionStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

final class InMemorySessionStore implements SessionStore {
	private final List<SessionRecord> sessions = new ArrayList<>();

	@Override
	public synchronized SessionRecord save(SessionRecord session) {
		if ((session.getType() == SessionType.WEB || session.getType() == SessionType.MINIPROGRAM)
			&& session.getStatus() == SessionStatus.ACTIVE
			&& findActiveByUserIdAndType(session.getUserId(), session.getType()).isPresent()) {
			throw new DuplicateKeyException("unique active session");
		}
		if (session.getId() == null) session.setId(UUID.randomUUID().toString());
		sessions.removeIf(existing -> existing.getId().equals(session.getId()));
		sessions.add(session);
		return session;
	}

	@Override public synchronized Optional<SessionRecord> findById(String id) {
		return sessions.stream().filter(session -> session.getId().equals(id)).findFirst();
	}
	@Override public synchronized Optional<SessionRecord> findByTokenHash(String tokenHash) {
		return sessions.stream().filter(session -> tokenHash.equals(session.getTokenHash())).findFirst();
	}
	@Override public synchronized Optional<SessionRecord> findActiveByUserIdAndType(String userId, SessionType type) {
		return sessions.stream().filter(session -> userId.equals(session.getUserId()) && session.getType() == type
			&& session.getStatus() == SessionStatus.ACTIVE).findFirst();
	}
	@Override public synchronized List<SessionRecord> findActiveByUserId(String userId) {
		return sessions.stream().filter(session -> userId.equals(session.getUserId())
			&& session.getStatus() == SessionStatus.ACTIVE).toList();
	}
	@Override public synchronized boolean transitionStatus(String sessionId, SessionStatus expected, SessionStatus target) {
		Optional<SessionRecord> current = findById(sessionId);
		if (current.isEmpty() || current.get().getStatus() != expected) return false;
		current.get().setStatus(target);
		return true;
	}
	@Override public synchronized long transitionAllActiveByUserId(String userId, SessionStatus target) {
		long changed = 0;
		for (SessionRecord session : sessions) {
			if (userId.equals(session.getUserId()) && session.getStatus() == SessionStatus.ACTIVE) {
				session.setStatus(target);
				changed++;
			}
		}
		return changed;
	}
	@Override public synchronized boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt) {
		Optional<SessionRecord> current = findById(sessionId);
		if (current.isEmpty() || current.get().getStatus() != SessionStatus.ACTIVE) return false;
		current.get().setLastAccessAt(lastAccessAt);
		current.get().setExpiresAt(expiresAt);
		return true;
	}
}

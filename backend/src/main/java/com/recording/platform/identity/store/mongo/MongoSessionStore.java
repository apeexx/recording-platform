package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.store.SessionStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Repository
public class MongoSessionStore implements SessionStore {
	private final SpringDataSessionRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoSessionStore(SpringDataSessionRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public SessionRecord save(SessionRecord session) {
		return repository.save(session);
	}

	@Override
	public Optional<SessionRecord> findById(String id) {
		return repository.findById(id);
	}

	@Override
	public Optional<SessionRecord> findByTokenHash(String tokenHash) {
		return repository.findByTokenHash(tokenHash);
	}

	@Override
	public Optional<SessionRecord> findActiveWebByUserId(String userId) {
		return repository.findFirstByUserIdAndTypeAndStatus(userId, SessionType.WEB, SessionStatus.ACTIVE);
	}

	@Override
	public List<SessionRecord> findActiveByUserId(String userId) {
		return repository.findAllByUserIdAndStatus(userId, SessionStatus.ACTIVE);
	}

	@Override
	public boolean transitionStatus(String sessionId, SessionStatus expected, SessionStatus target) {
		Query query = Query.query(Criteria.where("_id").is(sessionId).and("status").is(expected));
		Update update = new Update().set("status", target);
		return mongoTemplate.updateFirst(query, update, SessionRecord.class).getMatchedCount() == 1;
	}

	@Override
	public long transitionAllActiveByUserId(String userId, SessionStatus target) {
		Query query = Query.query(Criteria.where("userId").is(userId).and("status").is(SessionStatus.ACTIVE));
		Update update = new Update().set("status", target);
		return mongoTemplate.updateMulti(query, update, SessionRecord.class).getModifiedCount();
	}

	@Override
	public boolean touchActive(String sessionId, Instant lastAccessAt, Instant expiresAt) {
		Query query = Query.query(Criteria.where("_id").is(sessionId).and("status").is(SessionStatus.ACTIVE));
		Update update = new Update()
			.set("lastAccessAt", lastAccessAt)
			.set("expiresAt", expiresAt);
		return mongoTemplate.updateFirst(query, update, SessionRecord.class).getMatchedCount() == 1;
	}
}

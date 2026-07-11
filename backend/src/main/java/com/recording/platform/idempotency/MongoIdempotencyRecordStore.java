package com.recording.platform.idempotency;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MongoIdempotencyRecordStore implements IdempotencyRecordStore {
	private final SpringDataIdempotencyRecordRepository repository;

	public MongoIdempotencyRecordStore(SpringDataIdempotencyRecordRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean insertClaim(IdempotencyRecord record) {
		try {
			repository.insert(record);
			return true;
		} catch (DuplicateKeyException exception) {
			return false;
		}
	}

	@Override
	public Optional<IdempotencyRecord> find(String actorUserId, String action, String operationKey) {
		return repository.findByActorUserIdAndActionAndOperationKey(actorUserId, action, operationKey);
	}

	@Override public IdempotencyRecord save(IdempotencyRecord record) { return repository.save(record); }

	@Override
	public void deleteClaim(String actorUserId, String action, String operationKey) {
		repository.deleteByActorUserIdAndActionAndOperationKey(actorUserId, action, operationKey);
	}
}

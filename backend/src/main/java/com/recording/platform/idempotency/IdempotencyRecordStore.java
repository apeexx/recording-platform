package com.recording.platform.idempotency;

import java.util.Optional;

public interface IdempotencyRecordStore {
	boolean insertClaim(IdempotencyRecord record);
	Optional<IdempotencyRecord> find(String actorUserId, String action, String operationKey);
	IdempotencyRecord save(IdempotencyRecord record);
	void deleteClaim(String actorUserId, String action, String operationKey);
}

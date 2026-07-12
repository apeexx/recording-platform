package com.recording.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class IdempotencyServiceTests {
	@Test
	void concurrentDuplicateMutationExecutesOnceAndReplaysThePersistedResponse() throws Exception {
		InMemoryIdempotencyRecordStore records = new InMemoryIdempotencyRecordStore();
		IdempotencyService service = new IdempotencyService(
			records,
			new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin-1", null, "ROLE_ADMIN");
		AtomicInteger executions = new AtomicInteger();
		var executor = Executors.newFixedThreadPool(2);
		try {
			var calls = java.util.List.of(
				(java.util.concurrent.Callable<Result>) () -> service.execute(
					authentication, "task:create", "operation-1", Result.class,
					() -> new Result("task-" + executions.incrementAndGet())
				),
				(java.util.concurrent.Callable<Result>) () -> service.execute(
					authentication, "task:create", "operation-1", Result.class,
					() -> new Result("task-" + executions.incrementAndGet())
				)
			);
			var results = executor.invokeAll(calls);
			assertThat(results.get(0).get()).isEqualTo(new Result("task-1"));
			assertThat(results.get(1).get()).isEqualTo(new Result("task-1"));
		} finally {
			executor.shutdownNow();
		}

		assertThat(executions).hasValue(1);
		assertThat(records.data).hasSize(1);
		assertThat(records.data.values()).singleElement()
			.extracting(IdempotencyRecord::getStatus)
			.isEqualTo(IdempotencyStatus.COMPLETED);
	}

	record Result(String id) { }

	@Test
	void genericResponseTypeReplaysAListWithoutLosingElementTypes() {
		InMemoryIdempotencyRecordStore records = new InMemoryIdempotencyRecordStore();
		IdempotencyService service = new IdempotencyService(
			records,
			new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin-1", null, "ROLE_ADMIN");
		var type = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Result>>() { };

		service.execute(authentication, "batch:test", "batch-1", type, () -> java.util.List.of(new Result("one")));
		java.util.List<Result> replay = service.execute(
			authentication, "batch:test", "batch-1", type, () -> java.util.List.of(new Result("two"))
		);

		assertThat(replay).containsExactly(new Result("one"));
	}

	private static final class InMemoryIdempotencyRecordStore implements IdempotencyRecordStore {
		private final Map<String, IdempotencyRecord> data = new HashMap<>();
		@Override public synchronized boolean insertClaim(IdempotencyRecord record) {
			String key = key(record.getActorUserId(), record.getAction(), record.getOperationKey());
			if (data.containsKey(key)) return false;
			data.put(key, record);
			return true;
		}
		@Override public synchronized Optional<IdempotencyRecord> find(
			String actorUserId,
			String action,
			String operationKey
		) {
			return Optional.ofNullable(data.get(key(actorUserId, action, operationKey)));
		}
		@Override public synchronized IdempotencyRecord save(IdempotencyRecord record) {
			data.put(key(record.getActorUserId(), record.getAction(), record.getOperationKey()), record);
			return record;
		}
		@Override public synchronized void deleteClaim(String actorUserId, String action, String operationKey) {
			data.remove(key(actorUserId, action, operationKey));
		}
		private String key(String actor, String action, String operation) {
			return actor + ":" + action + ":" + operation;
		}
	}
}

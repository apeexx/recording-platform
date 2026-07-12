package com.recording.platform.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.api.ApiException;
import com.recording.platform.security.PlatformPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class IdempotencyService {
	private static final int LOCK_STRIPES = 256;
	private static final int REMOTE_WAIT_ATTEMPTS = 100;
	private final IdempotencyRecordStore records;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final ReentrantLock[] locks = createLocks();

	public IdempotencyService(IdempotencyRecordStore records, ObjectMapper objectMapper, Clock clock) {
		this.records = records;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public <T> T execute(
		Authentication authentication,
		String action,
		String operationKey,
		Class<T> responseType,
		Supplier<T> mutation
	) {
		return executeInternal(authentication, action, operationKey, () -> readResponse(responseType), mutation);
	}

	public <T> T execute(
		Authentication authentication,
		String action,
		String operationKey,
		TypeReference<T> responseType,
		Supplier<T> mutation
	) {
		return executeInternal(authentication, action, operationKey, () -> readResponse(responseType), mutation);
	}

	private <T> T executeInternal(
		Authentication authentication,
		String action,
		String operationKey,
		java.util.function.Supplier<java.util.function.Function<String, T>> readerFactory,
		Supplier<T> mutation
	) {
		String actorUserId = actorUserId(authentication);
		String normalizedAction = required(action, "IDEMPOTENCY_ACTION_REQUIRED", "幂等操作类型不能为空", 192);
		String normalizedKey = required(operationKey, "OPERATION_ID_REQUIRED", "Idempotency-Key 不能为空", 256);
		ReentrantLock lock = lock(actorUserId, normalizedAction, normalizedKey);
		lock.lock();
		try {
			IdempotencyRecord existing = records.find(actorUserId, normalizedAction, normalizedKey).orElse(null);
			if (existing != null) return replayOrWait(existing, readerFactory.get());

			Instant now = Instant.now(clock);
			IdempotencyRecord claim = new IdempotencyRecord();
			claim.setActorUserId(actorUserId);
			claim.setAction(normalizedAction);
			claim.setOperationKey(normalizedKey);
			claim.setStatus(IdempotencyStatus.IN_PROGRESS);
			claim.setCreatedAt(now);
			claim.setUpdatedAt(now);
			if (!records.insertClaim(claim)) {
				IdempotencyRecord concurrent = records.find(actorUserId, normalizedAction, normalizedKey)
					.orElseThrow(this::inProgress);
				return replayOrWait(concurrent, readerFactory.get());
			}

			T result;
			try {
				result = mutation.get();
			} catch (RuntimeException | Error exception) {
				records.deleteClaim(actorUserId, normalizedAction, normalizedKey);
				throw exception;
			}
			claim.setResponseJson(write(result));
			claim.setStatus(IdempotencyStatus.COMPLETED);
			claim.setUpdatedAt(Instant.now(clock));
			records.save(claim);
			return result;
		} finally {
			lock.unlock();
		}
	}

	private <T> T replayOrWait(IdempotencyRecord record, java.util.function.Function<String, T> reader) {
		IdempotencyRecord current = record;
		for (int attempt = 0; attempt <= REMOTE_WAIT_ATTEMPTS; attempt++) {
			if (current.getStatus() == IdempotencyStatus.COMPLETED) {
				return reader.apply(current.getResponseJson());
			}
			if (attempt == REMOTE_WAIT_ATTEMPTS) throw inProgress();
			try {
				TimeUnit.MILLISECONDS.sleep(50);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw inProgress();
			}
			current = records.find(current.getActorUserId(), current.getAction(), current.getOperationKey())
				.orElseThrow(this::inProgress);
		}
		throw inProgress();
	}

	private String actorUserId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
		}
		Object principal = authentication.getPrincipal();
		String actor = principal instanceof PlatformPrincipal platformPrincipal
			? platformPrincipal.userId() : authentication.getName();
		return required(actor, "AUTHENTICATION_REQUIRED", "请先登录", 256);
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_STORAGE_FAILED", "操作结果暂时无法保存");
		}
	}

	private <T> T read(String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED", "操作结果暂时无法重放");
		}
	}

	private <T> T read(String json, TypeReference<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_REPLAY_FAILED", "操作结果暂时无法重放");
		}
	}

	private <T> java.util.function.Function<String, T> readResponse(Class<T> type) {
		return (json) -> read(json, type);
	}

	private <T> java.util.function.Function<String, T> readResponse(TypeReference<T> type) {
		return (json) -> read(json, type);
	}

	private String required(String value, String code, String message, int maximumLength) {
		if (value == null || value.isBlank() || value.length() > maximumLength) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		return value.trim();
	}

	private ApiException inProgress() {
		return new ApiException(HttpStatus.CONFLICT, "OPERATION_IN_PROGRESS", "相同操作正在处理中，请稍后重试");
	}

	private ReentrantLock lock(String actor, String action, String operation) {
		return locks[Math.floorMod(java.util.Objects.hash(actor, action, operation), locks.length)];
	}

	private static ReentrantLock[] createLocks() {
		ReentrantLock[] values = new ReentrantLock[LOCK_STRIPES];
		for (int index = 0; index < values.length; index++) values[index] = new ReentrantLock();
		return values;
	}
}

package com.recording.platform.review.ai;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAccessService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskItemStore;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReviewAiJobService {
	private static final Duration RETENTION = Duration.ofHours(24);
	private static final Duration LEASE = Duration.ofMinutes(11);
	private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;

	private final ReviewAiJobRepository jobs;
	private final ReviewAiConfigService configs;
	private final ReviewAiProvider provider;
	private final TaskItemStore items;
	private final MediaAccessService media;
	private final TaskExecutor executor;
	private final MongoTemplate mongo;
	private final Clock clock;
	private final String workerId = UUID.randomUUID().toString();

	public ReviewAiJobService(
		ReviewAiJobRepository jobs,
		ReviewAiConfigService configs,
		ReviewAiProvider provider,
		TaskItemStore items,
		MediaAccessService media,
		@Qualifier("reviewAiTaskExecutor") TaskExecutor executor,
		MongoTemplate mongo,
		Clock clock
	) {
		this.jobs = jobs;
		this.configs = configs;
		this.provider = provider;
		this.items = items;
		this.media = media;
		this.executor = executor;
		this.mongo = mongo;
		this.clock = clock;
	}

	public ReviewAiJobView create(
		String itemId,
		ReviewAiJobType type,
		long expectedRevision,
		String operationId,
		PlatformPrincipal actor
	) {
		String normalizedOperationId = required(operationId);
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.REVIEWER) throw forbidden();
		ReviewAiJob existing = jobs.findByActorUserIdAndOperationId(actor.userId(), normalizedOperationId).orElse(null);
		if (existing != null) return ReviewAiJobView.from(existing);
		TaskItem item = items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务数据不存在"));
		if (item.getStatus() != TaskItemStatus.REVIEW_PENDING
			|| item.getRevision() != expectedRevision
			|| !actor.userId().equals(item.getReviewerId())
			|| item.getReviewAssignmentId() == null) {
			throw stale();
		}
		ReviewAiConfig config = configs.get(item.getTaskId(), actor);
		ReviewAiStageConfig stage = type == ReviewAiJobType.AUDIO_TRANSCRIBE
			? config.getAudio() : config.getText();
		if (!stage.enabled()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_AI_DISABLED", "该任务未启用此 AI 功能");
		}
		TaskItemResult result = item.getCurrentResult();
		String mediaId = result == null || result.audio() == null ? null : result.audio().mediaId();
		String sourceText = result == null ? null : trimToNull(result.text());
		if (type == ReviewAiJobType.AUDIO_TRANSCRIBE && mediaId == null) {
			throw invalid("REVIEW_AI_AUDIO_REQUIRED", "当前采集结果没有音频");
		}
		if (type == ReviewAiJobType.AUDIO_TRANSCRIBE
			&& result.audio().sizeBytes() > MAX_AUDIO_BYTES) {
			throw new ApiException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"REVIEW_AI_AUDIO_TOO_LARGE",
				"AI 音频转写仅支持不超过 20MB 的录音"
			);
		}
		if (type == ReviewAiJobType.TEXT_REFINE && sourceText == null) {
			throw invalid("REVIEW_AI_TEXT_REQUIRED", "当前采集结果没有文本");
		}
		provider.ensureConfigured();
		Instant now = Instant.now(clock);
		ReviewAiJob job = new ReviewAiJob();
		job.setId(UUID.randomUUID().toString());
		job.setTaskId(item.getTaskId());
		job.setItemId(itemId);
		job.setItemRevision(expectedRevision);
		job.setReviewAssignmentId(item.getReviewAssignmentId());
		job.setActorUserId(actor.userId());
		job.setOperationId(normalizedOperationId);
		job.setType(type);
		job.setStatus(ReviewAiJobStatus.PENDING);
		job.setConfigSnapshot(stage);
		job.setSourceMediaId(mediaId);
		job.setModel(stage.model());
		job.setCreatedAt(now);
		job.setUpdatedAt(now);
		job.setExpiresAt(now.plus(RETENTION));
		try {
			job = jobs.save(job);
		} catch (DuplicateKeyException exception) {
			return ReviewAiJobView.from(jobs.findByActorUserIdAndOperationId(
				actor.userId(), normalizedOperationId
			).orElseThrow());
		}
		if (!enqueue(job.getId())) {
			throw new ApiException(
				HttpStatus.TOO_MANY_REQUESTS,
				"REVIEW_AI_QUEUE_FULL",
				"AI 作业队列已满，请稍后重试"
			);
		}
		return ReviewAiJobView.from(job);
	}

	public ReviewAiJobView get(String jobId, PlatformPrincipal actor) {
		ReviewAiJob job = jobs.findById(jobId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_AI_JOB_NOT_FOUND", "AI 作业不存在"));
		if (actor == null || actor.role() != UserRole.ADMIN && !actor.userId().equals(job.getActorUserId())) {
			throw forbidden();
		}
		return ReviewAiJobView.from(job);
	}

	boolean enqueue(String jobId) {
		try {
			executor.execute(() -> process(jobId));
			return true;
		} catch (RejectedExecutionException exception) {
			failWithoutLease(jobId, "REVIEW_AI_QUEUE_FULL", "AI 作业队列已满，请稍后重试");
			return false;
		}
	}

	void process(String jobId) {
		ReviewAiJob job = acquire(jobId);
		if (job == null) return;
		long started = System.nanoTime();
		try {
			TaskItem item = items.findById(job.getItemId()).orElseThrow(this::stale);
			if (item.getRevision() != job.getItemRevision()
				|| item.getStatus() != TaskItemStatus.REVIEW_PENDING
				|| !job.getReviewAssignmentId().equals(item.getReviewAssignmentId())
				|| !job.getActorUserId().equals(item.getReviewerId())) {
				throw stale();
			}
			byte[] audio = null;
			String format = null;
			if (job.getType() == ReviewAiJobType.AUDIO_TRANSCRIBE) {
				var readable = media.openIntegrationRecording(job.getSourceMediaId(), job.getItemId());
				audio = Files.readAllBytes(readable.path());
				format = item.getCurrentResult().audio().format().name().toLowerCase(java.util.Locale.ROOT);
			}
			String sourceText = item.getCurrentResult() == null
				? null : trimToNull(item.getCurrentResult().text());
			ReviewAiProviderResult result = provider.generate(
				job.getType(), job.getConfigSnapshot(), sourceText, audio, format
			);
			complete(job, result, elapsed(started));
		} catch (ApiException exception) {
			fail(job, exception.getCode(), safeMessage(exception.getMessage()), elapsed(started));
		} catch (ReviewAiProviderException exception) {
			fail(job, exception.code(), safeMessage(exception.getMessage()), elapsed(started));
		} catch (Exception exception) {
			fail(job, "REVIEW_AI_FAILED", "AI 作业执行失败", elapsed(started));
		}
	}

	public void recover() {
		Instant now = Instant.now(clock);
		jobs.findByStatusOrStatusAndLeaseExpiresAtBefore(
			ReviewAiJobStatus.PENDING, ReviewAiJobStatus.PROCESSING, now
		).forEach(job -> enqueue(job.getId()));
	}

	private ReviewAiJob acquire(String jobId) {
		Instant now = Instant.now(clock);
		String leaseToken = UUID.randomUUID().toString();
		Criteria available = new Criteria().orOperator(
			Criteria.where("status").is(ReviewAiJobStatus.PENDING),
			new Criteria().andOperator(
				Criteria.where("status").is(ReviewAiJobStatus.PROCESSING),
				Criteria.where("leaseExpiresAt").lt(now)
			)
		);
		return mongo.findAndModify(
			Query.query(Criteria.where("_id").is(jobId)).addCriteria(available),
			new Update().set("status", ReviewAiJobStatus.PROCESSING)
				.set("leaseOwner", workerId).set("leaseToken", leaseToken)
				.set("leaseExpiresAt", now.plus(LEASE)).set("updatedAt", now),
			FindAndModifyOptions.options().returnNew(true),
			ReviewAiJob.class
		);
	}

	private void complete(ReviewAiJob job, ReviewAiProviderResult result, long durationMillis) {
		mongo.updateFirst(
			leased(job),
			new Update().set("status", ReviewAiJobStatus.COMPLETED)
				.set("resultText", result.text()).set("requestId", result.requestId())
				.set("durationMillis", durationMillis).set("updatedAt", Instant.now(clock))
				.unset("leaseOwner").unset("leaseToken").unset("leaseExpiresAt"),
			ReviewAiJob.class
		);
	}

	private void fail(ReviewAiJob job, String code, String message, long durationMillis) {
		mongo.updateFirst(
			leased(job),
			new Update().set("status", ReviewAiJobStatus.FAILED)
				.set("failureCode", code).set("failureMessage", message)
				.set("durationMillis", durationMillis).set("updatedAt", Instant.now(clock))
				.unset("leaseOwner").unset("leaseToken").unset("leaseExpiresAt"),
			ReviewAiJob.class
		);
	}

	private void failWithoutLease(String jobId, String code, String message) {
		mongo.updateFirst(
			Query.query(Criteria.where("_id").is(jobId).and("status").is(ReviewAiJobStatus.PENDING)),
			new Update().set("status", ReviewAiJobStatus.FAILED)
				.set("failureCode", code).set("failureMessage", message).set("updatedAt", Instant.now(clock)),
			ReviewAiJob.class
		);
	}

	private Query leased(ReviewAiJob job) {
		return Query.query(Criteria.where("_id").is(job.getId())
			.and("status").is(ReviewAiJobStatus.PROCESSING)
			.and("leaseOwner").is(workerId).and("leaseToken").is(job.getLeaseToken()));
	}

	private long elapsed(long started) {
		return (System.nanoTime() - started) / 1_000_000;
	}

	private String safeMessage(String message) {
		return message == null || message.isBlank() ? "AI 作业执行失败" : message;
	}

	private String required(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		return normalized;
	}

	private String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private ApiException stale() {
		return new ApiException(HttpStatus.CONFLICT, "STALE_STATE", "数据状态已变化，请刷新后重试");
	}

	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
	}
}

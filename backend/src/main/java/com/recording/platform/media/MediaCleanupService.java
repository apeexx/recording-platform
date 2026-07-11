package com.recording.platform.media;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MediaCleanupService {
	private static final Logger LOGGER = LoggerFactory.getLogger(MediaCleanupService.class);
	private final MediaCleanupJobStore jobs;
	private final RecordingMediaStorage storage;
	private final MediaAssetStore assets;
	private final TaskExecutor executor;
	private final Clock clock;

	public MediaCleanupService(
		MediaCleanupJobStore jobs,
		RecordingMediaStorage storage,
		MediaAssetStore assets,
		@Qualifier("importTaskExecutor") TaskExecutor executor,
		Clock clock
	) {
		this.jobs = jobs;
		this.storage = storage;
		this.assets = assets;
		this.executor = executor;
		this.clock = clock;
	}

	public MediaCleanupJob scheduleAndTry(
		String itemId,
		String operationId,
		List<String> relativePaths,
		List<String> mediaAssetIds
	) {
		MediaCleanupJob job = jobs.findByItemIdAndOperationId(itemId, operationId).orElse(null);
		if (job == null) {
			Instant now = Instant.now(clock);
			job = new MediaCleanupJob();
			job.setId(UUID.randomUUID().toString());
			job.setItemId(itemId);
			job.setOperationId(operationId);
			job.setRelativePaths(normalized(relativePaths));
			job.setMediaAssetIds(normalized(mediaAssetIds));
			job.setStatus(MediaCleanupStatus.PENDING);
			job.setCreatedAt(now);
			job.setUpdatedAt(now);
			try {
				job = jobs.save(job);
			} catch (DuplicateKeyException exception) {
				job = jobs.findByItemIdAndOperationId(itemId, operationId).orElseThrow(() -> exception);
			}
		}
		return tryCleanup(job);
	}

	public void retry(String itemId, String operationId) {
		try {
			jobs.findByItemIdAndOperationId(itemId, operationId)
				.filter((job) -> job.getStatus() == MediaCleanupStatus.PENDING)
				.ifPresent(this::tryCleanup);
		} catch (RuntimeException exception) {
			LOGGER.warn("Media cleanup replay retry could not be persisted for item {}", itemId);
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void scheduleStartupRecovery() {
		try {
			executor.execute(this::recoverPending);
		} catch (TaskRejectedException exception) {
			LOGGER.warn("Media cleanup startup recovery could not be queued");
		}
	}

	public void recoverPending() {
		List<MediaCleanupJob> pending;
		try {
			pending = jobs.findPending();
		} catch (RuntimeException exception) {
			LOGGER.warn("Media cleanup startup recovery could not list pending jobs");
			return;
		}
		for (MediaCleanupJob job : pending) {
			try {
				tryCleanup(job);
			} catch (RuntimeException exception) {
				LOGGER.warn("Media cleanup startup recovery could not persist job {}", job.getId());
			}
		}
	}

	private MediaCleanupJob tryCleanup(MediaCleanupJob job) {
		if (job.getStatus() == MediaCleanupStatus.COMPLETED) return job;
		job.setAttempt(job.getAttempt() + 1);
		job.setUpdatedAt(Instant.now(clock));
		job = jobs.save(job);
		List<RuntimeException> failures = new ArrayList<>();
		for (String relativePath : job.getRelativePaths()) {
			try {
				storage.delete(relativePath);
			} catch (RuntimeException exception) {
				failures.add(exception);
			}
		}
		for (String mediaAssetId : job.getMediaAssetIds()) {
			try {
				assets.deleteById(mediaAssetId);
			} catch (RuntimeException exception) {
				failures.add(exception);
			}
		}
		Instant now = Instant.now(clock);
		job.setUpdatedAt(now);
		if (failures.isEmpty()) {
			job.setStatus(MediaCleanupStatus.COMPLETED);
			job.setLastErrorSummary(null);
			job.setCompletedAt(now);
		} else {
			job.setStatus(MediaCleanupStatus.PENDING);
			job.setLastErrorSummary(errorSummary(failures.get(0)));
			LOGGER.warn("Media cleanup job {} remains pending after attempt {}", job.getId(), job.getAttempt());
		}
		return jobs.save(job);
	}

	private List<String> normalized(List<String> values) {
		if (values == null || values.isEmpty()) return new ArrayList<>();
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) result.add(value);
		}
		return new ArrayList<>(result);
	}

	private String errorSummary(RuntimeException exception) {
		String summary = exception.getClass().getSimpleName();
		return summary.length() > 128 ? summary.substring(0, 128) : summary;
	}
}

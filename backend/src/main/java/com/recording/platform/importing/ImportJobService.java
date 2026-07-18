package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.ImportRunMode;
import com.recording.platform.task.model.ImportJobStatus;
import com.recording.platform.task.model.ImportRowError;
import com.recording.platform.task.store.ImportJobStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportJobService {
	private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
	private static final int MAX_STORED_ROW_ERRORS = 1_000;
	private static final int PROGRESS_BATCH_SIZE = 100;
	private final ImportJobStore jobs;
	private final ImportFileParser parser;
	private final TaskItemCreationService itemCreation;
	private final ImportSourceStorage sources;
	private final TaskExecutor executor;
	private final Clock clock;

	public ImportJobService(
		ImportJobStore jobs,
		ImportFileParser parser,
		TaskItemCreationService itemCreation,
		ImportSourceStorage sources,
		@Qualifier("importTaskExecutor") TaskExecutor executor,
		Clock clock
	) {
		this.jobs = jobs;
		this.parser = parser;
		this.itemCreation = itemCreation;
		this.sources = sources;
		this.executor = executor;
		this.clock = clock;
	}

	public ImportJob create(String taskId, String operationId, MultipartFile file, PlatformPrincipal actor) {
		requireAdmin(actor);
		String idempotencyKey = required(operationId);
		ImportJob existing = jobs.findByTaskIdAndOperationId(taskId, idempotencyKey).orElse(null);
		if (existing != null) return existing;
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_REQUIRED", "导入文件不能为空");
		}
		ImportJob job = new ImportJob();
		job.setId(UUID.randomUUID().toString());
		job.setTaskId(taskId);
		job.setOperationId(idempotencyKey);
		job.setActorUserId(actor.userId());
		job.setActorUsername(actor.username() == null ? actor.name() : actor.username());
		job.setOriginalFilename(safeFilename(file.getOriginalFilename()));
		job.setStatus(ImportJobStatus.PENDING);
		job.setRunMode(ImportRunMode.FULL);
		job.setCreatedAt(Instant.now(clock));
		job.setUpdatedAt(job.getCreatedAt());
		StoredImportSource source = sources.save(job.getId(), file);
		job.setSourceRelativePath(source.relativePath());
		job.setFileSha256(source.sha256());
		job.setFileSizeBytes(source.sizeBytes());
		try {
			jobs.save(job);
		} catch (DuplicateKeyException exception) {
			sources.delete(source.relativePath());
			return jobs.findByTaskIdAndOperationId(taskId, idempotencyKey)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "IMPORT_JOB_CONFLICT", "导入任务已存在"));
		}
		enqueue(job.getId());
		return jobs.findById(job.getId()).orElse(job);
	}

	public ImportJob retry(String jobId, PlatformPrincipal actor) {
		requireAdmin(actor);
		ImportJob job = requireJob(jobId);
		if (job.getStatus() != ImportJobStatus.PARTIAL_SUCCESS && job.getStatus() != ImportJobStatus.FAILED) {
			throw new ApiException(HttpStatus.CONFLICT, "IMPORT_JOB_NOT_RETRYABLE", "当前导入任务没有可重试失败行");
		}
		Set<Long> failedRows = new HashSet<>();
		if (job.getRetryRowNumbers() != null) failedRows.addAll(job.getRetryRowNumbers());
		if (failedRows.isEmpty()) {
			for (ImportRowError error : job.getRowErrors()) failedRows.add(error.getRowNumber());
		}
		List<Long> rowsToRetry = failedRows.contains(0L) ? new ArrayList<>() : new ArrayList<>(failedRows);
		rowsToRetry.sort(Long::compareTo);
		job.setRetryRowNumbers(rowsToRetry);
		job.setRunMode(ImportRunMode.FAILED_ROWS);
		job.setStatus(ImportJobStatus.PENDING);
		job.setLeaseOwner(null);
		job.setLeaseExpiresAt(null);
		job.setHeartbeatAt(null);
		job.setUpdatedAt(Instant.now(clock));
		jobs.save(job);
		enqueue(jobId);
		return requireJob(jobId);
	}

	public ImportJob get(String jobId) { return requireJob(jobId); }

	@EventListener(ApplicationReadyEvent.class)
	public void scheduleStartupRecovery() {
		try {
			executor.execute(this::recoverStaleJobs);
		} catch (TaskRejectedException ignored) {
			// A later operational retry can invoke recovery after executor capacity returns.
		}
	}

	public void recoverStaleJobs() {
		List<ImportJob> recoverable;
		try {
			recoverable = jobs.findRecoverable(Instant.now(clock));
		} catch (RuntimeException exception) {
			return;
		}
		for (ImportJob job : recoverable) enqueue(job.getId());
	}

	private void process(String jobId) {
		String workerId = UUID.randomUUID().toString();
		Instant leaseStartedAt = Instant.now(clock);
		ImportJob job = jobs.acquireLease(
			jobId, workerId, leaseStartedAt, leaseStartedAt.plus(LEASE_DURATION)
		).orElse(null);
		if (job == null) return;
		String originalSourcePath = job.getSourceRelativePath();
		String preparedRetryPath = null;
		Set<Long> retryRows = job.getRunMode() == ImportRunMode.FAILED_ROWS
			&& job.getRetryRowNumbers() != null && !job.getRetryRowNumbers().isEmpty()
			? new HashSet<>(job.getRetryRowNumbers()) : null;
		try {
			java.nio.file.Path sourcePath = sources.resolve(job.getSourceRelativePath());
			List<ImportRow> rows = parser.parse(sourcePath, sourcePath.getFileName().toString());
			long totalRows = retryRows == null ? rows.size() : job.getTotalRows();
			long success = retryRows == null ? 0 : job.getSuccessRows();
			long failure = 0;
			int attempted = 0;
			List<ImportRowError> errors = new ArrayList<>();
			Set<Long> failedRowNumbers = new HashSet<>();
			PlatformPrincipal actor = new PlatformPrincipal(
				"import-" + job.getId(), job.getActorUserId(), job.getActorUsername(), job.getActorUsername(),
				UserRole.ADMIN, SessionType.WEB, false
			);
			for (ImportRow row : rows) {
				if (retryRows != null && !retryRows.contains(row.rowNumber())) continue;
				job = renewLease(job, workerId);
				try {
					itemCreation.add(
						job.getTaskId(),
						new AddTaskItemCommand(
							row.referenceText(), row.referenceAudioUrl(), row.referenceVideoUrl()
						),
						job.getOperationId() + ":row:" + row.rowNumber(),
						actor
					);
					success++;
				} catch (ApiException exception) {
					failure++;
					failedRowNumbers.add(row.rowNumber());
					addBoundedError(errors, row.rowNumber(), exception.getCode(), exception.getMessage());
				} catch (RuntimeException exception) {
					failure++;
					failedRowNumbers.add(row.rowNumber());
					addBoundedError(errors, row.rowNumber(), "IMPORT_ROW_FAILED", "该行导入失败");
				}
				attempted++;
				if (attempted % PROGRESS_BATCH_SIZE == 0) {
					job = persistProgress(job, workerId, totalRows, success, failure, errors, failedRowNumbers);
				}
			}
			job.setTotalRows(totalRows);
			job.setSuccessRows(success);
			job.setRowErrors(new ArrayList<>(errors));
			job.setFailureRows(failure);
			job.setRetryRowNumbers(sorted(failedRowNumbers));
			job.setStatus(failure == 0 ? ImportJobStatus.COMPLETED
				: success > 0 ? ImportJobStatus.PARTIAL_SUCCESS : ImportJobStatus.FAILED);
			if (failure > 0 && success > 0) {
				preparedRetryPath = sources.retainFailedRows(
					job.getId(), workerId, rows, failedRowNumbers
				);
				job.setSourceRelativePath(preparedRetryPath);
			}
		} catch (ApiException exception) {
			job.setStatus(ImportJobStatus.FAILED);
			job.setRowErrors(List.of(rowError(0, exception.getCode(), exception.getMessage())));
			job.setFailureRows(job.getRowErrors().size());
			job.setRetryRowNumbers(new ArrayList<>());
		}
		job.setCompletedAt(Instant.now(clock));
		job.setUpdatedAt(job.getCompletedAt());
		if (job.getStatus() == ImportJobStatus.COMPLETED) job.setRetryRowNumbers(new ArrayList<>());
		ImportJob finished = jobs.finish(job, workerId).orElse(null);
		if (finished == null) {
			if (preparedRetryPath != null) sources.delete(preparedRetryPath);
			return;
		}
		if (finished.getStatus() == ImportJobStatus.COMPLETED) {
			sources.delete(originalSourcePath);
		} else if (preparedRetryPath != null && !preparedRetryPath.equals(originalSourcePath)) {
			sources.delete(originalSourcePath);
		}
	}

	private ImportJob renewLease(ImportJob job, String workerId) {
		Instant now = Instant.now(clock);
		Instant expiresAt = now.plus(LEASE_DURATION);
		return jobs.heartbeat(job.getId(), workerId, now, expiresAt)
			.orElseThrow(() -> new IllegalStateException("import lease lost"));
	}

	private ImportJob persistProgress(
		ImportJob job,
		String workerId,
		long totalRows,
		long success,
		long failure,
		List<ImportRowError> errors,
		Set<Long> failedRowNumbers
	) {
		job.setTotalRows(totalRows);
		job.setSuccessRows(success);
		job.setFailureRows(failure);
		job.setRowErrors(new ArrayList<>(errors));
		job.setRetryRowNumbers(sorted(failedRowNumbers));
		job.setUpdatedAt(Instant.now(clock));
		return jobs.saveProgress(job, workerId)
			.orElseThrow(() -> new IllegalStateException("import lease lost"));
	}

	private void addBoundedError(List<ImportRowError> errors, long rowNumber, String code, String message) {
		if (errors.size() < MAX_STORED_ROW_ERRORS) errors.add(rowError(rowNumber, code, message));
	}

	private List<Long> sorted(Set<Long> rows) {
		List<Long> values = new ArrayList<>(rows);
		values.sort(Long::compareTo);
		return values;
	}

	private void enqueue(String jobId) {
		try {
			executor.execute(() -> process(jobId));
		} catch (TaskRejectedException exception) {
			ImportJob job = requireJob(jobId);
			job.setStatus(ImportJobStatus.FAILED);
			job.setRowErrors(List.of(rowError(0, "IMPORT_QUEUE_UNAVAILABLE", "导入队列暂时不可用")));
			job.setFailureRows(1);
			job.setCompletedAt(Instant.now(clock));
			job.setUpdatedAt(job.getCompletedAt());
			jobs.save(job);
		}
	}

	private ImportRowError rowError(long rowNumber, String code, String message) {
		ImportRowError error = new ImportRowError();
		error.setRowNumber(rowNumber);
		error.setCode(code == null ? "IMPORT_ROW_FAILED" : code);
		String sanitized = message == null ? "该行导入失败" : message.replaceAll("(?i)https?://\\S+", "[URL]");
		error.setMessage(sanitized.length() > 256 ? sanitized.substring(0, 256) : sanitized);
		return error;
	}

	private ImportJob requireJob(String jobId) {
		return jobs.findById(jobId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IMPORT_JOB_NOT_FOUND", "导入任务不存在"));
	}

	private void requireAdmin(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
	}

	private String required(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "Idempotency-Key 不能为空");
		}
		return value.trim();
	}

	private String safeFilename(String filename) {
		if (filename == null || filename.isBlank()) return "import.csv";
		return java.nio.file.Path.of(filename).getFileName().toString().replaceAll("[\\r\\n]", "");
	}
}

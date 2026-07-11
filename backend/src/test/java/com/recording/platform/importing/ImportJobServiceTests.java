package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.ImportJobStatus;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.ImportJobStore;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class ImportJobServiceTests {
	@TempDir
	Path tempDir;

	@Test
	void importIsIdempotentSupportsPartialSuccessAndRetriesOnlyFailedRows() throws Exception {
		InMemoryImportJobStore jobs = new InMemoryImportJobStore();
		TaskItemCreationService itemCreation = org.mockito.Mockito.mock(TaskItemCreationService.class);
		TaskItem created = new TaskItem();
		doReturn(created).when(itemCreation).add(eq("task-1"), any(), any(), any());
		doThrow(new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"ITEM_REFERENCE_INVALID",
			"https://signed.example.com/private?token=must-not-leak"
		)).doReturn(created).when(itemCreation).add(
			eq("task-1"),
			argThat((AddTaskItemCommand command) -> "bad-2".equals(command.externalItemId())),
			any(),
			any()
		);
		TaskExecutor direct = Runnable::run;
		ImportJobService service = new ImportJobService(
			jobs,
			new ImportFileParser(),
			itemCreation,
			new ImportSourceStorage(tempDir),
			direct,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		MockMultipartFile file = new MockMultipartFile(
			"file", "items.csv", "text/csv",
			("externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\n"
				+ "good-1,第一条,https://signed-success.example/audio.wav?token=success-token,\n"
				+ "bad-2,第二条,,\n").getBytes()
		);
		PlatformPrincipal admin = new PlatformPrincipal(
			"session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);

		ImportJob first = service.create("task-1", "import-1", file, admin);
		ImportJob duplicate = service.create("task-1", "import-1", file, admin);

		assertThat(duplicate.getId()).isEqualTo(first.getId());
		assertThat(first.getStatus()).isEqualTo(ImportJobStatus.PARTIAL_SUCCESS);
		assertThat(first.getTotalRows()).isEqualTo(2);
		assertThat(first.getSuccessRows()).isEqualTo(1);
		assertThat(first.getFailureRows()).isEqualTo(1);
		assertThat(first.getRowErrors()).singleElement().satisfies((error) -> {
			assertThat(error.getRowNumber()).isEqualTo(3);
			assertThat(error.getCode()).isEqualTo("ITEM_REFERENCE_INVALID");
			assertThat(error.getMessage()).doesNotContain("signed.example.com").doesNotContain("token");
		});
		String retainedRetrySource = Files.readString(tempDir.resolve(first.getSourceRelativePath()));
		assertThat(retainedRetrySource)
			.doesNotContain("signed-success.example")
			.doesNotContain("success-token");
		verify(itemCreation, times(2)).add(eq("task-1"), any(), any(), any());

		ImportJob retried = service.retry(first.getId(), admin);
		assertThat(retried.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
		assertThat(retried.getSuccessRows()).isEqualTo(2);
		assertThat(retried.getFailureRows()).isZero();
		assertThat(retried.getRowErrors()).isEmpty();
		verify(itemCreation, times(3)).add(eq("task-1"), any(), any(), any());
	}

	@Test
	void parserLevelFailureRetriesTheWholeSourceInsteadOfSkippingEveryRow() {
		InMemoryImportJobStore jobs = new InMemoryImportJobStore();
		ImportFileParser parser = org.mockito.Mockito.mock(ImportFileParser.class);
		TaskItemCreationService itemCreation = org.mockito.Mockito.mock(TaskItemCreationService.class);
		doReturn(new TaskItem()).when(itemCreation).add(eq("task-1"), any(), any(), any());
		org.mockito.Mockito.when(parser.parse(any(), any()))
			.thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IMPORT_FILE_INVALID", "解析失败"))
			.thenReturn(List.of(new ImportRow(2, "row-1", "第一条", null, null)));
		ImportJobService service = new ImportJobService(
			jobs, parser, itemCreation, new ImportSourceStorage(tempDir), Runnable::run,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		MockMultipartFile file = new MockMultipartFile(
			"file", "items.csv", "text/csv",
			("externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\nrow-1,第一条,,\n").getBytes()
		);
		PlatformPrincipal admin = new PlatformPrincipal(
			"session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);

		ImportJob failed = service.create("task-1", "import-parser", file, admin);
		assertThat(failed.getStatus()).isEqualTo(ImportJobStatus.FAILED);
		ImportJob retried = service.retry(failed.getId(), admin);

		assertThat(retried.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
		assertThat(retried.getSuccessRows()).isEqualTo(1);
		verify(itemCreation, times(1)).add(eq("task-1"), any(), any(), any());
	}

	@Test
	void startupRecoveryRequeuesOnlyExpiredProcessingLeases() throws Exception {
		InMemoryImportJobStore jobs = new InMemoryImportJobStore();
		TaskItemCreationService itemCreation = org.mockito.Mockito.mock(TaskItemCreationService.class);
		doReturn(new TaskItem()).when(itemCreation).add(eq("task-1"), any(), any(), any());
		ImportSourceStorage sources = new ImportSourceStorage(tempDir);
		ImportJob stale = recoverableJob("job-stale", Instant.parse("2026-07-11T11:59:00Z"));
		jobs.save(stale);
		Path staleSource = sources.resolve(stale.getSourceRelativePath());
		Files.createDirectories(staleSource.getParent());
		Files.writeString(
			staleSource,
			"externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\nrow-1,第一条,,\n"
		);
		ImportJob active = recoverableJob("job-active", Instant.parse("2026-07-11T12:10:00Z"));
		jobs.save(active);
		Path activeSource = sources.resolve(active.getSourceRelativePath());
		Files.createDirectories(activeSource.getParent());
		Files.writeString(
			activeSource,
			"externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\nrow-2,第二条,,\n"
		);
		ImportJobService service = new ImportJobService(
			jobs,
			new ImportFileParser(),
			itemCreation,
			sources,
			Runnable::run,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);

		service.recoverStaleJobs();

		ImportJob recovered = jobs.findById("job-stale").orElseThrow();
		assertThat(recovered.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
		assertThat(recovered.getAttempt()).isEqualTo(1);
		assertThat(recovered.getLeaseOwner()).isNull();
		assertThat(jobs.findById("job-active").orElseThrow().getStatus()).isEqualTo(ImportJobStatus.PROCESSING);
		verify(itemCreation, times(1)).add(eq("task-1"), any(), any(), any());
	}

	@Test
	void largeFailureSetsCapStoredErrorsAndPersistProgressInBatches() {
		InMemoryImportJobStore jobs = new InMemoryImportJobStore();
		TaskItemCreationService itemCreation = org.mockito.Mockito.mock(TaskItemCreationService.class);
		doThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ROW_INVALID", "该行不合法"))
			.when(itemCreation).add(eq("task-1"), any(), any(), any());
		ImportJobService service = new ImportJobService(
			jobs,
			new ImportFileParser(),
			itemCreation,
			new ImportSourceStorage(tempDir),
			Runnable::run,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		StringBuilder csv = new StringBuilder(
			"externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\n"
		);
		for (int index = 1; index <= 1005; index++) csv.append("row-").append(index).append(",文本,,\n");
		MockMultipartFile file = new MockMultipartFile(
			"file", "large.csv", "text/csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)
		);
		PlatformPrincipal admin = new PlatformPrincipal(
			"session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false
		);

		ImportJob job = service.create("task-1", "import-large", file, admin);

		assertThat(job.getStatus()).isEqualTo(ImportJobStatus.FAILED);
		assertThat(job.getTotalRows()).isEqualTo(1005);
		assertThat(job.getFailureRows()).isEqualTo(1005);
		assertThat(job.getRowErrors()).hasSize(1000);
		assertThat(jobs.saveCount).isGreaterThanOrEqualTo(12);
	}

	private ImportJob recoverableJob(String id, Instant leaseExpiresAt) {
		ImportJob job = new ImportJob();
		job.setId(id);
		job.setTaskId("task-1");
		job.setOperationId("operation-" + id);
		job.setActorUserId("admin-1");
		job.setActorUsername("admin");
		job.setOriginalFilename("items.csv");
		job.setSourceRelativePath("temp/imports/" + id + "/source.csv");
		job.setStatus(ImportJobStatus.PROCESSING);
		job.setLeaseOwner("old-worker");
		job.setLeaseExpiresAt(leaseExpiresAt);
		job.setCreatedAt(Instant.parse("2026-07-11T11:00:00Z"));
		job.setUpdatedAt(job.getCreatedAt());
		return job;
	}

	private static final class InMemoryImportJobStore implements ImportJobStore {
		private final Map<String, ImportJob> data = new HashMap<>();
		private int saveCount;
		@Override public ImportJob save(ImportJob job) {
			saveCount++;
			data.put(job.getId(), job);
			return job;
		}
		@Override public Optional<ImportJob> findById(String id) { return Optional.ofNullable(data.get(id)); }
		@Override public Optional<ImportJob> findByTaskIdAndOperationId(String taskId, String operationId) {
			return data.values().stream().filter((job) -> taskId.equals(job.getTaskId())
				&& operationId.equals(job.getOperationId())).findFirst();
		}
		@Override public synchronized Optional<ImportJob> acquireLease(
			String jobId,
			String workerId,
			Instant now,
			Instant leaseExpiresAt
		) {
			ImportJob job = data.get(jobId);
			if (job == null || job.getStatus() != ImportJobStatus.PENDING
				&& (job.getStatus() != ImportJobStatus.PROCESSING
					|| job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isAfter(now))) {
				return Optional.empty();
			}
			job.setStatus(ImportJobStatus.PROCESSING);
			job.setLeaseOwner(workerId);
			job.setLeaseExpiresAt(leaseExpiresAt);
			job.setHeartbeatAt(now);
			job.setStartedAt(now);
			job.setUpdatedAt(now);
			job.setAttempt(job.getAttempt() + 1);
			return Optional.of(job);
		}
		@Override public synchronized boolean heartbeat(
			String jobId,
			String workerId,
			Instant now,
			Instant leaseExpiresAt
		) {
			ImportJob job = data.get(jobId);
			if (job == null || job.getStatus() != ImportJobStatus.PROCESSING
				|| !workerId.equals(job.getLeaseOwner())) return false;
			job.setHeartbeatAt(now);
			job.setLeaseExpiresAt(leaseExpiresAt);
			job.setUpdatedAt(now);
			return true;
		}
		@Override public synchronized List<ImportJob> findRecoverable(Instant now) {
			return data.values().stream().filter((job) -> job.getStatus() == ImportJobStatus.PENDING
				|| job.getStatus() == ImportJobStatus.PROCESSING
				&& (job.getLeaseExpiresAt() == null || !job.getLeaseExpiresAt().isAfter(now))).toList();
		}
	}
}

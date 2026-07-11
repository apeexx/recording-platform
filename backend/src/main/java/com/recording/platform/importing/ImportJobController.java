package com.recording.platform.importing;

import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.ImportJobStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import-jobs")
public class ImportJobController {
	private final ImportJobService jobs;
	private final IdempotencyService idempotency;
	public ImportJobController(ImportJobService jobs, IdempotencyService idempotency) {
		this.jobs = jobs;
		this.idempotency = idempotency;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ImportAcceptedResponse create(
		@RequestParam String taskId,
		@RequestParam MultipartFile file,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "import:create:" + taskId, operationId, ImportAcceptedResponse.class, () -> {
			ImportJob job = jobs.create(taskId, operationId, file, actor);
			return new ImportAcceptedResponse(job.getId(), job.getStatus());
		});
	}

	@GetMapping("/{jobId}") public ImportJob get(@PathVariable String jobId) { return jobs.get(jobId); }
	@PostMapping("/{jobId}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
	public ImportAcceptedResponse retry(
		@PathVariable String jobId,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(authentication, "import:retry:" + jobId, operationId, ImportAcceptedResponse.class, () -> {
			ImportJob job = jobs.retry(jobId, actor);
			return new ImportAcceptedResponse(job.getId(), job.getStatus());
		});
	}

	public record ImportAcceptedResponse(String importJobId, ImportJobStatus status) { }
}

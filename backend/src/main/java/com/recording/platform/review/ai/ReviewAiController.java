package com.recording.platform.review.ai;

import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewAiController {
	private final ReviewAiConfigService configs;
	private final ReviewAiJobService jobs;
	private final IdempotencyService idempotency;

	public ReviewAiController(
		ReviewAiConfigService configs,
		ReviewAiJobService jobs,
		IdempotencyService idempotency
	) {
		this.configs = configs;
		this.jobs = jobs;
		this.idempotency = idempotency;
	}

	@GetMapping("/tasks/{taskId}/ai-config")
	public ReviewAiConfig getConfig(
		@PathVariable String taskId,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return configs.get(taskId, actor);
	}

	@PutMapping("/tasks/{taskId}/ai-config")
	public ReviewAiConfig putConfig(
		@PathVariable String taskId,
		@Valid @RequestBody ConfigRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		@AuthenticationPrincipal PlatformPrincipal actor,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication,
			"review-ai-config:" + taskId,
			operationId,
			ReviewAiConfig.class,
			() -> configs.put(taskId, request.audio(), request.text(), actor)
		);
	}

	@PostMapping("/{itemId}/ai-jobs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ReviewAiJobView create(
		@PathVariable String itemId,
		@Valid @RequestBody JobRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return jobs.create(itemId, request.type(), request.expectedRevision(), request.operationId(), actor);
	}

	@GetMapping("/ai-jobs/{jobId}")
	public ReviewAiJobView get(
		@PathVariable String jobId,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return jobs.get(jobId, actor);
	}

	public record ConfigRequest(
		@NotNull ReviewAiStageConfig audio,
		@NotNull ReviewAiStageConfig text
	) { }

	public record JobRequest(
		@NotNull ReviewAiJobType type,
		long expectedRevision,
		@NotBlank String operationId
	) { }
}

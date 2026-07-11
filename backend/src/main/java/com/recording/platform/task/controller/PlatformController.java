package com.recording.platform.task.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.service.PlatformCommand;
import com.recording.platform.task.service.PlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {
	private final PlatformService platforms;
	private final IdempotencyService idempotency;
	public PlatformController(PlatformService platforms, IdempotencyService idempotency) {
		this.platforms = platforms;
		this.idempotency = idempotency;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PlatformRecord create(
		@Valid @RequestBody PlatformRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication, "platform:create", operationId, PlatformRecord.class,
			() -> platforms.create(request.command())
		);
	}

	@GetMapping
	public PageResponse<PlatformRecord> list(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) { return platforms.list(page, size); }

	@GetMapping("/{id}") public PlatformRecord get(@PathVariable String id) { return platforms.get(id); }
	@PutMapping("/{id}") public PlatformRecord update(
		@PathVariable String id,
		@Valid @RequestBody PlatformRequest request,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		return idempotency.execute(
			authentication, "platform:update:" + id, operationId, PlatformRecord.class,
			() -> platforms.update(id, request.command())
		);
	}
	@DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(
		@PathVariable String id,
		@RequestHeader("Idempotency-Key") String operationId,
		Authentication authentication
	) {
		idempotency.execute(authentication, "platform:delete:" + id, operationId, Boolean.class, () -> {
			platforms.delete(id);
			return Boolean.TRUE;
		});
	}

	public record PlatformRequest(@NotNull String code, @NotNull String name, String description, Boolean active) {
		PlatformCommand command() { return new PlatformCommand(code, name, description, active); }
	}
}

package com.recording.platform.operation.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.operation.dto.OperationView;
import com.recording.platform.operation.service.OperationService;
import com.recording.platform.security.PlatformPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OperationController {
	private final OperationService operations;

	public OperationController(OperationService operations) { this.operations = operations; }

	@GetMapping("/task-items/{itemId}/operations")
	public PageResponse<OperationView> itemOperations(
		@PathVariable String itemId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return operations.itemOperations(itemId, page, size, actor);
	}

	@GetMapping("/operations")
	public PageResponse<OperationView> globalOperations(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return operations.globalOperations(page, size, actor);
	}
}

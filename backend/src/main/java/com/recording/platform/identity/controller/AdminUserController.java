package com.recording.platform.identity.controller;

import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.dto.UserResponse;
import com.recording.platform.identity.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
	private final AdminUserService users;

	public AdminUserController(AdminUserService users) {
		this.users = users;
	}

	@PostMapping
	public UserResponse create(@Valid @RequestBody CreateBackendUserRequest request) {
		return users.create(request);
	}

	@GetMapping
	public Page<UserResponse> list(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		return users.list(page, size);
	}

	@PostMapping("/{userId}/disable")
	public UserResponse disable(@PathVariable String userId) {
		return users.disable(userId);
	}
}

package com.recording.platform.identity.controller;

import com.recording.platform.identity.dto.CollectorAccountLoginRequest;
import com.recording.platform.identity.dto.CollectorProfileResponse;
import com.recording.platform.identity.dto.CompleteCollectorProfileRequest;
import com.recording.platform.identity.dto.MiniProgramSessionResponse;
import com.recording.platform.identity.dto.SetCollectorNameRequest;
import com.recording.platform.identity.dto.UserResponse;
import com.recording.platform.identity.dto.UpdateCollectorPasswordRequest;
import com.recording.platform.identity.dto.WeChatLoginRequest;
import com.recording.platform.identity.service.CollectorIdentityService;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/miniprogram")
public class MiniProgramAuthenticationController {
	private final WeChatAuthenticationService authentication;
	private final CollectorIdentityService collectors;

	public MiniProgramAuthenticationController(
		WeChatAuthenticationService authentication,
		CollectorIdentityService collectors
	) {
		this.authentication = authentication;
		this.collectors = collectors;
	}

	@PostMapping("/login")
	public MiniProgramSessionResponse login(@Valid @RequestBody WeChatLoginRequest request) {
		return MiniProgramSessionResponse.from(authentication.login(request.code()));
	}

	@PostMapping("/account-login")
	public MiniProgramSessionResponse accountLogin(@Valid @RequestBody CollectorAccountLoginRequest request) {
		return MiniProgramSessionResponse.from(collectors.login(request.account(), request.password()));
	}

	@GetMapping("/profile")
	public CollectorProfileResponse profile(@AuthenticationPrincipal PlatformPrincipal principal) {
		return CollectorProfileResponse.from(collectors.profile(principal.userId()));
	}

	@PostMapping("/profile/complete")
	public CollectorProfileResponse completeProfile(
		@AuthenticationPrincipal PlatformPrincipal principal,
		@Valid @RequestBody CompleteCollectorProfileRequest request
	) {
		return CollectorProfileResponse.from(collectors.completeProfile(
			principal.userId(), request.name(), request.account(), request.password()
		));
	}

	@PutMapping("/name")
	public UserResponse setName(
		@AuthenticationPrincipal PlatformPrincipal principal,
		@Valid @RequestBody SetCollectorNameRequest request
	) {
		return UserResponse.from(collectors.setName(principal.userId(), request.name()));
	}

	@PutMapping("/password")
	public CollectorProfileResponse changePassword(
		@AuthenticationPrincipal PlatformPrincipal principal,
		@Valid @RequestBody UpdateCollectorPasswordRequest request
	) {
		return CollectorProfileResponse.from(collectors.changePassword(
			principal.userId(), request.currentPassword(), request.newPassword()
		));
	}
}

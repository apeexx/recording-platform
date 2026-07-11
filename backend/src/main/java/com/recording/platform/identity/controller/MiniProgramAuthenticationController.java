package com.recording.platform.identity.controller;

import com.recording.platform.identity.dto.MiniProgramSessionResponse;
import com.recording.platform.identity.dto.SetCollectorNameRequest;
import com.recording.platform.identity.dto.UserResponse;
import com.recording.platform.identity.dto.WeChatLoginRequest;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/miniprogram")
public class MiniProgramAuthenticationController {
	private final WeChatAuthenticationService authentication;

	public MiniProgramAuthenticationController(WeChatAuthenticationService authentication) {
		this.authentication = authentication;
	}

	@PostMapping("/login")
	public MiniProgramSessionResponse login(@Valid @RequestBody WeChatLoginRequest request) {
		return MiniProgramSessionResponse.from(authentication.login(request.code()));
	}

	@PutMapping("/name")
	public UserResponse setName(
		@AuthenticationPrincipal PlatformPrincipal principal,
		@Valid @RequestBody SetCollectorNameRequest request
	) {
		return UserResponse.from(authentication.setName(principal.userId(), request.name()));
	}
}

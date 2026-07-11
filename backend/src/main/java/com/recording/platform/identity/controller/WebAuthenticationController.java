package com.recording.platform.identity.controller;

import com.recording.platform.identity.dto.ChangePasswordRequest;
import com.recording.platform.identity.dto.TakeoverRequest;
import com.recording.platform.identity.dto.WebLoginRequest;
import com.recording.platform.identity.dto.WebSessionResponse;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.service.WebLoginResult;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.security.SessionAuthenticationFilter;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/web")
public class WebAuthenticationController {
	private final WebAuthenticationService authentication;
	private final boolean secureCookie;
	private final Duration cookieLifetime;

	public WebAuthenticationController(
		WebAuthenticationService authentication,
		@Value("${recording.web-session.cookie-secure:false}") boolean secureCookie,
		@Value("${recording.web-session.idle-hours:12}") long idleHours
	) {
		this.authentication = authentication;
		this.secureCookie = secureCookie;
		this.cookieLifetime = Duration.ofHours(Math.max(idleHours, 1));
	}

	@PostMapping("/login")
	public ResponseEntity<WebSessionResponse> login(@Valid @RequestBody WebLoginRequest request) {
		return sessionResponse(authentication.login(request.username(), request.password()));
	}

	@PostMapping("/takeover")
	public ResponseEntity<WebSessionResponse> takeover(@Valid @RequestBody TakeoverRequest request) {
		return sessionResponse(authentication.takeover(request.takeoverToken()));
	}

	@GetMapping("/me")
	public WebSessionResponse me(@AuthenticationPrincipal PlatformPrincipal principal) {
		return WebSessionResponse.from(principal);
	}

	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken csrfToken) {
		return Map.of(
			"headerName", csrfToken.getHeaderName(),
			"parameterName", csrfToken.getParameterName(),
			"token", csrfToken.getToken()
		);
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, Object>> logout(@AuthenticationPrincipal PlatformPrincipal principal) {
		authentication.logout(principal.sessionId());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, clearCookie().toString())
			.body(Map.of("success", true));
	}

	@PutMapping("/password")
	public ResponseEntity<Map<String, Object>> changePassword(
		@AuthenticationPrincipal PlatformPrincipal principal,
		@Valid @RequestBody ChangePasswordRequest request
	) {
		authentication.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, clearCookie().toString())
			.body(Map.of("success", true, "reloginRequired", true));
	}

	private ResponseEntity<WebSessionResponse> sessionResponse(WebLoginResult result) {
		ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.WEB_COOKIE_NAME, result.token())
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite("Lax")
			.path("/")
			.maxAge(cookieLifetime)
			.build();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(WebSessionResponse.from(result));
	}

	private ResponseCookie clearCookie() {
		return ResponseCookie.from(SessionAuthenticationFilter.WEB_COOKIE_NAME, "")
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
	}
}

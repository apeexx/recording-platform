package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebAuthenticationService {
	private final UserStore users;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public WebAuthenticationService(
		UserStore users,
		SessionService sessions,
		PasswordEncoder passwordEncoder,
		Clock clock
	) {
		this.users = users;
		this.sessions = sessions;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public WebLoginResult login(String username, String password) {
		UserAccount user = users.findByUsername(normalizeUsername(username)).orElseThrow(this::invalidCredentials);
		if (user.getStatus() != UserStatus.ACTIVE
			|| user.getRole() == UserRole.COLLECTOR
			|| !passwordEncoder.matches(nullToEmpty(password), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		var activeSession = sessions.activeWeb(user.getId());
		if (activeSession.isPresent()) {
			throw accountInUse(user, activeSession.get().getId());
		}
		try {
			return toResult(user, sessions.issueWeb(user.getId()));
		} catch (DuplicateKeyException exception) {
			String activeSessionId = sessions.activeWeb(user.getId())
				.map((session) -> session.getId())
				.orElseThrow(() -> new ApiException(
					HttpStatus.CONFLICT,
					"ACCOUNT_IN_USE",
					"账号登录状态已变化，请重试"
				));
			throw accountInUse(user, activeSessionId);
		}
	}

	public WebLoginResult takeover(String takeoverToken) {
		IssuedSession issued = sessions.confirmTakeover(takeoverToken);
		UserAccount user = users.findById(issued.session().getUserId()).orElseThrow(this::invalidCredentials);
		return toResult(user, issued);
	}

	public void logout(String sessionId) {
		sessions.revoke(sessionId);
	}

	public void changePassword(String userId, String currentPassword, String newPassword) {
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (!passwordEncoder.matches(nullToEmpty(currentPassword), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		if (!BcryptPasswordPolicy.isValidForEncoding(newPassword)) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"PASSWORD_TOO_WEAK",
				"新密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节"
			);
		}
		String nextPasswordHash = passwordEncoder.encode(newPassword);
		if (!users.updatePasswordIfActive(
			userId,
			user.getPasswordHash(),
			nextPasswordHash,
			Instant.now(clock)
		)) {
			UserAccount current = users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
			if (current.getStatus() != UserStatus.ACTIVE) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
			}
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试");
		}
		sessions.revokeAll(userId);
	}

	private WebLoginResult toResult(UserAccount user, IssuedSession issued) {
		return new WebLoginResult(
			issued.token(),
			issued.session().getId(),
			user.getId(),
			user.getUsername(),
			user.getName(),
			user.getRole(),
			user.isFirstPasswordChangeRequired()
		);
	}

	private String normalizeUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
	}

	private ApiException accountInUse(UserAccount user, String activeSessionId) {
		IssuedSession takeover = sessions.issueTakeover(user.getId(), activeSessionId);
		return new ApiException(
			HttpStatus.CONFLICT,
			"ACCOUNT_IN_USE",
			"账号已在其他设备登录，确认后可接管",
			Map.of("takeoverToken", takeover.token())
		);
	}
}

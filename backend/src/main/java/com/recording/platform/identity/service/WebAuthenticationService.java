package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.WebUserStore;
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
	private final WebUserStore users;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public WebAuthenticationService(
		WebUserStore users,
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
		WebUser user = users.findByUsername(normalizeUsername(username)).orElseThrow(this::invalidCredentials);
		if (user.getStatus() != UserStatus.ACTIVE
			|| !passwordEncoder.matches(nullToEmpty(password), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		var activeSession = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.WEB);
		if (activeSession.isPresent()) {
			throw accountInUse(user, activeSession.get().getId());
		}
		try {
			return toResult(user, sessions.issueWeb(user.getId()));
		} catch (DuplicateKeyException exception) {
			String activeSessionId = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.WEB)
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
		IssuedSession issued = sessions.confirmWebTakeover(takeoverToken);
		WebUser user = users.findById(issued.session().getUserId()).orElseThrow(this::invalidCredentials);
		return toResult(user, issued);
	}

	public void logout(String sessionId) {
		sessions.revoke(sessionId);
	}

	public void changePassword(String userId, String currentPassword, String newPassword) {
		WebUser user = users.findById(userId)
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
			WebUser current = users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
			if (current.getStatus() != UserStatus.ACTIVE) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
			}
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试");
		}
		sessions.revokeAll(userId);
	}

	public void changeInitialPassword(String userId, String newPassword) {
		WebUser user = requireActiveInitialPasswordUser(userId);
		validateNewPassword(newPassword);
		if (!users.updateInitialPasswordIfRequired(
			user.getId(), passwordEncoder.encode(newPassword), Instant.now(clock)
		)) {
			throw accountStateChanged();
		}
		sessions.revokeAll(userId);
	}

	public void skipInitialPasswordChange(String userId) {
		WebUser user = requireActiveInitialPasswordUser(userId);
		if (!users.clearInitialPasswordChangeIfRequired(user.getId(), Instant.now(clock))) {
			throw accountStateChanged();
		}
	}

	private WebUser requireActiveInitialPasswordUser(String userId) {
		WebUser user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		if (!user.isFirstPasswordChangeRequired()) throw accountStateChanged();
		return user;
	}

	private void validateNewPassword(String newPassword) {
		if (!BcryptPasswordPolicy.isValidForEncoding(newPassword)) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"PASSWORD_TOO_WEAK",
				"新密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节"
			);
		}
	}

	private ApiException accountStateChanged() {
		return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试");
	}

	private WebLoginResult toResult(WebUser user, IssuedSession issued) {
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

	private ApiException accountInUse(WebUser user, String activeSessionId) {
		IssuedSession takeover = sessions.issueWebTakeover(user.getId(), activeSessionId);
		return new ApiException(
			HttpStatus.CONFLICT,
			"ACCOUNT_IN_USE",
			"账号已在其他设备登录，确认后可接管",
			Map.of("takeoverToken", takeover.token())
		);
	}
}

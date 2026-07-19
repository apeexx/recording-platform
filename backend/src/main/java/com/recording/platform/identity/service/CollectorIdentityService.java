package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CollectorIdentityService {
	private final UserStore users;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public CollectorIdentityService(
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

	public MiniProgramLoginResult login(String account, String password) {
		String normalized = normalizeAccount(account);
		UserAccount user = users.findByUsername(normalized).orElseThrow(this::invalidCredentials);
		if (user.getRole() != UserRole.COLLECTOR
			|| user.getStatus() != UserStatus.ACTIVE
			|| !passwordEncoder.matches(nullToEmpty(password), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		IssuedSession issued = sessions.issueMiniProgram(user.getId());
		return new MiniProgramLoginResult(issued.token(), issued.session().getId(), user);
	}

	public UserAccount profile(String userId) {
		return requireCollector(userId);
	}

	public UserAccount completeProfile(String userId, String name, String account, String password) {
		UserAccount current = requireCollector(userId);
		validateName(name);
		String normalized = validateAccount(account);
		validatePassword(password);
		if (StringUtils.hasText(current.getUsername()) || StringUtils.hasText(current.getPasswordHash())) {
			throw new ApiException(HttpStatus.CONFLICT, "PROFILE_ALREADY_COMPLETED", "登录账号已设置");
		}
		Optional<UserAccount> existing = users.findByUsername(normalized);
		if (existing.isPresent() && !userId.equals(existing.get().getId())) {
			throw usernameExists();
		}
		try {
			return users.completeCollectorProfileIfActive(
				userId,
				normalized,
				name.trim(),
				passwordEncoder.encode(password),
				Instant.now(clock)
			).orElseThrow(() -> new ApiException(
				HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"
			));
		} catch (DuplicateKeyException exception) {
			throw usernameExists();
		}
	}

	public UserAccount setName(String userId, String name) {
		validateName(name);
		return users.updateCollectorNameIfActive(userId, name.trim(), Instant.now(clock))
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
	}

	public UserAccount changePassword(String userId, String currentPassword, String newPassword) {
		UserAccount user = requireCollector(userId);
		if (!passwordEncoder.matches(nullToEmpty(currentPassword), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		validatePassword(newPassword);
		UserAccount saved = users.updateCollectorPasswordIfActive(
			userId,
			user.getPasswordHash(),
			passwordEncoder.encode(newPassword),
			Instant.now(clock)
		).orElseThrow(() -> new ApiException(
			HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"
		));
		sessions.revokeAll(userId);
		return saved;
	}

	private UserAccount requireCollector(String userId) {
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() != UserRole.COLLECTOR) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTOR_REQUIRED", "仅录音人员可以管理个人资料");
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		return user;
	}

	private void validateName(String name) {
		if (!StringUtils.hasText(name) || name.trim().length() > 64) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NAME", "姓名不能为空且不能超过 64 个字符");
		}
	}

	private String validateAccount(String account) {
		String normalized = normalizeAccount(account);
		if (!CollectorProfilePolicy.isValidAccount(normalized)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COLLECTOR_ACCOUNT", "登录账号必须为 6 到 12 位数字且首位不能为 0");
		}
		return normalized;
	}

	private void validatePassword(String password) {
		if (!BcryptPasswordPolicy.isValidForEncoding(password)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_TOO_WEAK", "密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节");
		}
	}

	private String normalizeAccount(String account) {
		return account == null ? "" : account.trim();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
	}

	private ApiException usernameExists() {
		return new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该登录账号已被使用");
	}
}

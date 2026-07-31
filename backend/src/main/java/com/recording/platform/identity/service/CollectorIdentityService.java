package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.MiniProgramUserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Map;

@Service
public class CollectorIdentityService {
	private final MiniProgramUserStore users;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public CollectorIdentityService(
		MiniProgramUserStore users,
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
		MiniProgramUser user = users.findByAccount(normalized).orElseThrow(this::invalidCredentials);
		if (user.getStatus() != UserStatus.ACTIVE
			|| !passwordEncoder.matches(nullToEmpty(password), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		IssuedSession issued = issueSession(user);
		return new MiniProgramLoginResult(issued.token(), issued.session().getId(), user);
	}

	public MiniProgramLoginResult takeover(String takeoverToken) {
		IssuedSession issued = sessions.confirmMiniProgramTakeover(takeoverToken);
		MiniProgramUser user = users.findById(issued.session().getUserId()).orElseThrow(this::invalidCredentials);
		return new MiniProgramLoginResult(issued.token(), issued.session().getId(), user);
	}

	public MiniProgramUser profile(String userId) {
		return requireCollector(userId);
	}

	public MiniProgramUser completeProfile(String userId, String name, String account, String password) {
		MiniProgramUser current = requireCollector(userId);
		String normalizedName = validateName(name);
		boolean accountProvided = StringUtils.hasText(account);
		boolean passwordProvided = StringUtils.hasText(password);
		if (accountProvided != passwordProvided) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ACCOUNT_PASSWORD_REQUIRED",
				"数字登录账号和密码必须同时填写或同时留空"
			);
		}
		if (!accountProvided) {
			return setName(userId, name);
		}
		String normalized = validateAccount(account);
		validatePassword(password);
		if (StringUtils.hasText(current.getAccount()) || StringUtils.hasText(current.getPasswordHash())) {
			throw new ApiException(HttpStatus.CONFLICT, "PROFILE_ALREADY_COMPLETED", "登录账号已设置");
		}
		ensureNameAvailable(userId, normalizedName);
		Optional<MiniProgramUser> existing = users.findByAccount(normalized);
		if (existing.isPresent() && !userId.equals(existing.get().getId())) {
			throw usernameExists();
		}
		try {
			return users.completeProfileIfActive(
				userId,
				normalized,
				normalizedName,
				passwordEncoder.encode(password),
				Instant.now(clock)
			).orElseThrow(() -> new ApiException(
				HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"
			));
		} catch (DuplicateKeyException exception) {
			if (isNameDuplicate(exception) || nameOwnedByOther(userId, normalizedName)) {
				throw nameExists();
			}
			throw usernameExists();
		}
	}

	public MiniProgramUser setName(String userId, String name) {
		String normalizedName = validateName(name);
		ensureNameAvailable(userId, normalizedName);
		try {
			return users.updateNameIfActive(userId, normalizedName, Instant.now(clock))
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
		} catch (DuplicateKeyException exception) {
			throw nameExists();
		}
	}

	public MiniProgramUser changePassword(String userId, String currentPassword, String newPassword) {
		MiniProgramUser user = requireCollector(userId);
		if (!passwordEncoder.matches(nullToEmpty(currentPassword), nullToEmpty(user.getPasswordHash()))) {
			throw invalidCredentials();
		}
		validatePassword(newPassword);
		MiniProgramUser saved = users.updatePasswordIfActive(
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

	private MiniProgramUser requireCollector(String userId) {
		MiniProgramUser user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		return user;
	}

	private IssuedSession issueSession(MiniProgramUser user) {
		var active = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.MINIPROGRAM);
		if (active.isPresent()) {
			IssuedSession takeover = sessions.issueMiniProgramTakeover(user.getId(), active.get().getId());
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号已在其他设备登录，确认后可接管",
				Map.of("takeoverToken", takeover.token()));
		}
		try {
			return sessions.issueMiniProgram(user.getId());
		} catch (DuplicateKeyException exception) {
			String activeId = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.MINIPROGRAM)
				.map(session -> session.getId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号登录状态已变化，请重试"));
			IssuedSession takeover = sessions.issueMiniProgramTakeover(user.getId(), activeId);
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号已在其他设备登录，确认后可接管",
				Map.of("takeoverToken", takeover.token()));
		}
	}

	private String validateName(String name) {
		if (!StringUtils.hasText(name) || name.trim().length() > 64) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NAME", "姓名不能为空且不能超过 64 个字符");
		}
		return name.trim();
	}

	private void ensureNameAvailable(String userId, String name) {
		if (nameOwnedByOther(userId, name)) throw nameExists();
	}

	private boolean nameOwnedByOther(String userId, String name) {
		return users.findByName(name).filter(existing -> !userId.equals(existing.getId())).isPresent();
	}

	private boolean isNameDuplicate(DuplicateKeyException exception) {
		return exception.getMessage() != null && exception.getMessage().contains("unique_miniprogram_name");
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

	private ApiException nameExists() {
		return new ApiException(HttpStatus.CONFLICT, "NAME_EXISTS", "该姓名已被使用");
	}
}

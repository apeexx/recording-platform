package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.dto.UserResponse;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {
	private final UserStore users;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public AdminUserService(
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

	public UserResponse create(CreateBackendUserRequest request) {
		if (request.role() != UserRole.ADMIN && request.role() != UserRole.REVIEWER) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_BACKEND_ROLE",
				"后台账号角色只能是 ADMIN 或 REVIEWER"
			);
		}
		if (!BcryptPasswordPolicy.isValidForEncoding(request.initialPassword())) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"PASSWORD_TOO_WEAK",
				"初始密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节"
			);
		}
		String username = request.username().trim().toLowerCase(Locale.ROOT);
		if (users.findByUsername(username).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
		}
		Instant now = Instant.now(clock);
		UserAccount user = new UserAccount();
		user.setInternalUserNo(UserNumbers.next());
		user.setUsername(username);
		user.setName(request.name().trim());
		user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
		user.setRole(request.role());
		user.setStatus(UserStatus.ACTIVE);
		user.setFirstPasswordChangeRequired(true);
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		return UserResponse.from(users.save(user));
	}

	public Page<UserResponse> list(int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		return users.findAllBackend(PageRequest.of(safePage, safeSize)).map(UserResponse::from);
	}

	public Page<UserResponse> search(String query, UserRole role, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		return users.search(query == null ? "" : query.trim(), role, PageRequest.of(safePage, safeSize))
			.map(UserResponse::from);
	}

	public UserResponse resetPassword(String userId, String newPassword) {
		if (!BcryptPasswordPolicy.isValidForEncoding(newPassword)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_TOO_WEAK",
				"新密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节");
		}
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() == UserRole.COLLECTOR) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BACKEND_ROLE", "只能重置后台账号密码");
		}
		UserAccount saved = users.resetBackendPasswordIfActive(
			userId, passwordEncoder.encode(newPassword), Instant.now(clock)
		).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
		sessions.revokeAll(userId);
		return UserResponse.from(saved);
	}

	public UserResponse disable(String userId) {
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() == UserRole.COLLECTOR) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BACKEND_ROLE", "只能停用后台账号");
		}
		UserAccount saved;
		if (user.getStatus() == UserStatus.DISABLED) {
			saved = user;
		} else {
			saved = users.disableBackendIfActive(userId, Instant.now(clock))
				.orElseGet(() -> users.findById(userId).orElseThrow(() ->
					new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试")
				));
			if (saved.getStatus() != UserStatus.DISABLED) {
				throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试");
			}
		}
		sessions.revokeAll(userId);
		return UserResponse.from(saved);
	}
}

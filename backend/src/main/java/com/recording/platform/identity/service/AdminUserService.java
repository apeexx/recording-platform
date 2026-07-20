package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.dto.UserResponse;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {
	private static final Sort GLOBAL_SEARCH_SORT = Sort.by(Sort.Direction.DESC, "createdAt")
		.and(Sort.by(Sort.Direction.ASC, "id"));
	private static final Comparator<UserResponse> GLOBAL_RESPONSE_ORDER = Comparator
		.comparing(UserResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
		.thenComparing(UserResponse::id);
	private final WebUserStore webUsers;
	private final MiniProgramUserStore miniProgramUsers;
	private final IdentityDirectory identities;
	private final SessionService sessions;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public AdminUserService(
		WebUserStore webUsers,
		MiniProgramUserStore miniProgramUsers,
		IdentityDirectory identities,
		SessionService sessions,
		PasswordEncoder passwordEncoder,
		Clock clock
	) {
		this.webUsers = webUsers; this.miniProgramUsers = miniProgramUsers; this.identities = identities;
		this.sessions = sessions; this.passwordEncoder = passwordEncoder; this.clock = clock;
	}

	public UserResponse create(CreateBackendUserRequest request) {
		if (request.role() != UserRole.ADMIN && request.role() != UserRole.REVIEWER) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BACKEND_ROLE", "后台账号角色只能是 ADMIN 或 REVIEWER");
		}
		validatePassword(request.initialPassword(), "初始密码");
		String username = request.username().trim().toLowerCase(Locale.ROOT);
		if (webUsers.findByUsername(username).isPresent()) throw usernameExists();
		Instant now = Instant.now(clock);
		WebUser user = new WebUser(); user.setId(IdentityIds.web()); user.setUsername(username); user.setName(request.name().trim());
		user.setPasswordHash(passwordEncoder.encode(request.initialPassword())); user.setRole(request.role()); user.setStatus(UserStatus.ACTIVE);
		user.setFirstPasswordChangeRequired(true); user.setCreatedAt(now); user.setUpdatedAt(now);
		try { return UserResponse.from(webUsers.save(user)); } catch (DuplicateKeyException exception) { throw usernameExists(); }
	}

	public Page<UserResponse> list(int page, int size) {
		return webUsers.findAll(page(page, size)).map(UserResponse::from);
	}

	public Page<UserResponse> search(String query, UserRole role, int page, int size) {
		String term = query == null ? "" : query.trim(); PageRequest pageable = page(page, size);
		if (role == UserRole.COLLECTOR) return miniProgramUsers.search(term, pageable).map(UserResponse::from);
		if (role != null) return webUsers.search(term, role, pageable).map(UserResponse::from);
		int fetchSize = (int) Math.min(Integer.MAX_VALUE, pageable.getOffset() + pageable.getPageSize());
		PageRequest mergeWindow = PageRequest.of(0, fetchSize, GLOBAL_SEARCH_SORT);
		Page<WebUser> webPage = webUsers.search(term, null, mergeWindow);
		Page<MiniProgramUser> miniPage = miniProgramUsers.search(term, mergeWindow);
		var merged = new ArrayList<UserResponse>(webPage.getNumberOfElements() + miniPage.getNumberOfElements());
		webPage.getContent().stream().map(UserResponse::from).forEach(merged::add);
		miniPage.getContent().stream().map(UserResponse::from).forEach(merged::add);
		merged.sort(GLOBAL_RESPONSE_ORDER);
		int from = Math.min((int) pageable.getOffset(), merged.size());
		int to = Math.min(from + pageable.getPageSize(), merged.size());
		return new PageImpl<>(merged.subList(from, to), pageable, webPage.getTotalElements() + miniPage.getTotalElements());
	}

	public UserResponse resetPassword(String userId, String newPassword) {
		validatePassword(newPassword, "新密码");
		var identity = identities.findById(userId).orElseThrow(this::notFound);
		Instant now = Instant.now(clock);
		UserResponse response;
		if (identity.role() == UserRole.COLLECTOR) {
			MiniProgramUser saved = miniProgramUsers.resetPasswordIfActive(userId, passwordEncoder.encode(newPassword), now)
				.orElseThrow(this::stateChanged);
			response = UserResponse.from(saved);
		} else {
			WebUser saved = webUsers.resetPasswordIfActive(userId, passwordEncoder.encode(newPassword), now)
				.orElseThrow(this::stateChanged);
			response = UserResponse.from(saved);
		}
		sessions.revokeAll(userId); return response;
	}

	public UserResponse updateCollectorAccount(String userId, String account) {
		String normalized = account == null ? "" : account.trim();
		if (!CollectorProfilePolicy.isValidAccount(normalized)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COLLECTOR_ACCOUNT", "登录账号必须为 6 到 12 位数字且首位不能为 0");
		}
		MiniProgramUser current = miniProgramUsers.findById(userId).orElseThrow(this::notFound);
		var existing = miniProgramUsers.findByAccount(normalized);
		if (existing.isPresent() && !userId.equals(existing.get().getId())) throw usernameExists();
		try {
			MiniProgramUser saved = miniProgramUsers.updateAccountIfActive(userId, normalized, Instant.now(clock)).orElseThrow(this::stateChanged);
			sessions.revokeAll(userId); return UserResponse.from(saved);
		} catch (DuplicateKeyException exception) { throw usernameExists(); }
	}

	public UserResponse disable(String userId) {
		WebUser user = webUsers.findById(userId).orElseThrow(this::notFound);
		WebUser saved = user.getStatus() == UserStatus.DISABLED ? user : webUsers.disableIfActive(userId, Instant.now(clock))
			.orElseThrow(this::stateChanged);
		sessions.revokeAll(userId); return UserResponse.from(saved);
	}

	private PageRequest page(int page, int size) { return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)); }
	private void validatePassword(String password, String label) {
		if (!BcryptPasswordPolicy.isValidForEncoding(password)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
			"PASSWORD_TOO_WEAK", label + "至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节");
	}
	private ApiException usernameExists() { return new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该登录账号已被使用"); }
	private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"); }
	private ApiException stateChanged() { return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"); }
}

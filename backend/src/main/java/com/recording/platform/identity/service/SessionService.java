package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionRecord;
import com.recording.platform.identity.model.SessionStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.model.UserType;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.SessionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public class SessionService {
	private static final Duration TAKEOVER_LIFETIME = Duration.ofMinutes(5);
	private final SessionStore sessions;
	private final IdentityDirectory identities;
	private final OpaqueTokenService tokens;
	private final Clock clock;
	private final Duration webIdleTimeout;
	private final Duration miniProgramLifetime;

	public SessionService(
		SessionStore sessions,
		IdentityDirectory identities,
		OpaqueTokenService tokens,
		Clock clock,
		Duration webIdleTimeout,
		Duration miniProgramLifetime
	) {
		this.sessions = sessions;
		this.identities = identities;
		this.tokens = tokens;
		this.clock = clock;
		this.webIdleTimeout = webIdleTimeout;
		this.miniProgramLifetime = miniProgramLifetime;
	}

	public IssuedSession issueWeb(String userId) {
		return issue(userId, SessionType.WEB, webIdleTimeout, null);
	}

	public IssuedSession issueMiniProgram(String userId) {
		return issue(userId, SessionType.MINIPROGRAM, miniProgramLifetime, null);
	}

	public IssuedSession issueWebTakeover(String userId, String replacedSessionId) {
		return issue(userId, SessionType.WEB_TAKEOVER, TAKEOVER_LIFETIME, replacedSessionId);
	}

	public IssuedSession issueMiniProgramTakeover(String userId, String replacedSessionId) {
		return issue(userId, SessionType.MINIPROGRAM_TAKEOVER, TAKEOVER_LIFETIME, replacedSessionId);
	}

	public IssuedSession confirmWebTakeover(String rawToken) {
		return confirmTakeover(rawToken, SessionType.WEB_TAKEOVER, SessionType.WEB, UserType.WEB);
	}

	public IssuedSession confirmMiniProgramTakeover(String rawToken) {
		return confirmTakeover(rawToken, SessionType.MINIPROGRAM_TAKEOVER, SessionType.MINIPROGRAM, UserType.MINIPROGRAM);
	}

	private IssuedSession confirmTakeover(
		String rawToken,
		SessionType takeoverType,
		SessionType sessionType,
		UserType userType
	) {
		SessionRecord takeover = sessions.findByTokenHash(tokens.hash(rawToken))
			.orElseThrow(this::invalidTakeover);
		Instant now = Instant.now(clock);
		if (takeover.getType() != takeoverType
			|| takeover.getStatus() != SessionStatus.ACTIVE
			|| !takeover.getExpiresAt().isAfter(now)) {
			throw invalidTakeover();
		}
		SessionRecord replaced = sessions.findById(takeover.getReplacedSessionId()).orElseThrow(this::invalidTakeover);
		if (!takeover.getUserId().equals(replaced.getUserId()) || replaced.getType() != sessionType
			|| replaced.getStatus() != SessionStatus.ACTIVE || !matchesUserType(takeover.getUserId(), userType)) {
			throw invalidTakeover();
		}
		if (!sessions.transitionStatus(takeover.getId(), SessionStatus.ACTIVE, SessionStatus.USED)) {
			throw invalidTakeover();
		}
		if (!sessions.transitionStatus(
			takeover.getReplacedSessionId(),
			SessionStatus.ACTIVE,
			SessionStatus.REPLACED
		)) {
			throw invalidTakeover();
		}
		IdentityUser user = activeUser(takeover.getUserId(), userType);
		try {
			return sessionType == SessionType.WEB ? issueWeb(user.id()) : issueMiniProgram(user.id());
		} catch (RuntimeException exception) {
			try {
				sessions.transitionStatus(
					takeover.getReplacedSessionId(),
					SessionStatus.REPLACED,
					SessionStatus.ACTIVE
				);
			} catch (RuntimeException restoreFailure) {
				exception.addSuppressed(restoreFailure);
			}
			throw exception;
		}
	}

	public SessionIdentity authenticateWeb(String rawToken) {
		return authenticate(rawToken, SessionType.WEB);
	}

	public SessionIdentity authenticateMiniProgram(String rawToken) {
		return authenticate(rawToken, SessionType.MINIPROGRAM);
	}

	public void revoke(String sessionId) {
		sessions.transitionStatus(sessionId, SessionStatus.ACTIVE, SessionStatus.REVOKED);
	}

	public void revokeAll(String userId) {
		sessions.transitionAllActiveByUserId(userId, SessionStatus.REVOKED);
	}

	public java.util.Optional<SessionRecord> activeWeb(String userId) {
		return active(userId, SessionType.WEB);
	}

	public java.util.Optional<SessionRecord> active(String userId, SessionType type) {
		return sessions.findActiveByUserIdAndType(userId, type).filter(this::isNotExpired);
	}

	private IssuedSession issue(
		String userId,
		SessionType type,
		Duration lifetime,
		String replacedSessionId
	) {
		OpaqueTokenService.TokenPair token = tokens.issue();
		Instant now = Instant.now(clock);
		SessionRecord session = new SessionRecord();
		session.setUserId(userId);
		session.setTokenHash(token.hash());
		session.setType(type);
		session.setStatus(SessionStatus.ACTIVE);
		session.setReplacedSessionId(replacedSessionId);
		session.setCreatedAt(now);
		session.setLastAccessAt(now);
		session.setExpiresAt(now.plus(lifetime));
		return new IssuedSession(token.raw(), sessions.save(session));
	}

	private SessionIdentity authenticate(String rawToken, SessionType expectedType) {
		SessionRecord session = sessions.findByTokenHash(tokens.hash(rawToken))
			.orElseThrow(this::invalidSession);
		if (session.getStatus() == SessionStatus.REPLACED) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REPLACED", "会话已被新设备接管");
		}
		if (session.getType() != expectedType || session.getStatus() != SessionStatus.ACTIVE) {
			throw invalidSession();
		}
		Instant now = Instant.now(clock);
		if (!session.getExpiresAt().isAfter(now)) {
			sessions.transitionStatus(session.getId(), SessionStatus.ACTIVE, SessionStatus.EXPIRED);
			throw invalidSession();
		}
		UserType userType = expectedType == SessionType.WEB ? UserType.WEB : UserType.MINIPROGRAM;
		if (!matchesUserType(session.getUserId(), userType)) throw invalidSession();
		IdentityUser user = activeUser(session.getUserId(), userType);
		Instant nextExpiry = expectedType == SessionType.WEB
			? now.plus(webIdleTimeout)
			: session.getExpiresAt();
		if (!sessions.touchActive(session.getId(), now, nextExpiry)) {
			SessionRecord current = sessions.findById(session.getId()).orElseThrow(this::invalidSession);
			if (current.getStatus() == SessionStatus.REPLACED) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REPLACED", "会话已被新设备接管");
			}
			throw invalidSession();
		}
		return new SessionIdentity(
			session.getId(),
			user.id(),
			user.loginName(),
			user.name(),
			user.role(),
			session.getType(),
			user.firstPasswordChangeRequired()
		);
	}

	private IdentityUser activeUser(String userId, UserType expectedType) {
		IdentityUser user = identities.findById(userId).orElseThrow(this::invalidSession);
		if (user.userType() != expectedType) throw invalidSession();
		if (user.status() != UserStatus.ACTIVE) {
			revokeAll(userId);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		return user;
	}

	private boolean matchesUserType(String userId, UserType type) {
		return userId != null && (type == UserType.WEB ? userId.startsWith("WEB-") : userId.startsWith("MINI-"));
	}

	private boolean isNotExpired(SessionRecord session) {
		if (session.getExpiresAt() != null && session.getExpiresAt().isAfter(Instant.now(clock))) {
			return true;
		}
		sessions.transitionStatus(session.getId(), SessionStatus.ACTIVE, SessionStatus.EXPIRED);
		return false;
	}

	private ApiException invalidSession() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "会话无效或已过期");
	}

	private ApiException invalidTakeover() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "TAKEOVER_TOKEN_INVALID", "接管凭证无效或已使用");
	}
}

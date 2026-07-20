package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatIdentity;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;

@Service
public class WeChatAuthenticationService {
	private final MiniProgramUserStore users;
	private final SessionService sessions;
	private final WeChatClient weChatClient;
	private final Clock clock;

	public WeChatAuthenticationService(
		MiniProgramUserStore users,
		SessionService sessions,
		WeChatClient weChatClient,
		Clock clock
	) {
		this.users = users;
		this.sessions = sessions;
		this.weChatClient = weChatClient;
		this.clock = clock;
	}

	public MiniProgramLoginResult login(String code) {
		WeChatIdentity identity = weChatClient.exchange(code);
		MiniProgramUser user = findOrCreateCollector(identity);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		IssuedSession issued = issueSession(user);
		return new MiniProgramLoginResult(issued.token(), issued.session().getId(), user);
	}

	private MiniProgramUser findOrCreateCollector(WeChatIdentity identity) {
		var existing = users.findByWechatIdentity(identity.appId(), identity.openId());
		if (existing.isPresent()) return existing.get();
		try {
			return createCollector(identity);
		} catch (DuplicateKeyException exception) {
			return users.findByWechatIdentity(identity.appId(), identity.openId())
				.orElseThrow(() -> new ApiException(
					HttpStatus.CONFLICT,
					"ACCOUNT_STATE_CHANGED",
					"账号状态已变化，请重试"
				));
		}
	}

	public MiniProgramUser setName(String userId, String name) {
		if (!StringUtils.hasText(name) || name.trim().length() > 64) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NAME", "姓名不能为空且不能超过 64 个字符");
		}
		MiniProgramUser user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTOR_REQUIRED", "仅录音人员可以设置姓名");
		}
		return users.updateNameIfActive(userId, name.trim(), Instant.now(clock))
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
	}

	private MiniProgramUser createCollector(WeChatIdentity identity) {
		Instant now = Instant.now(clock);
		MiniProgramUser user = new MiniProgramUser();
		user.setId(IdentityIds.miniProgram());
		user.setStatus(UserStatus.ACTIVE);
		user.setWechatAppId(identity.appId());
		user.setWechatOpenId(identity.openId());
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		return users.save(user);
	}

	private IssuedSession issueSession(MiniProgramUser user) {
		var active = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.MINIPROGRAM);
		if (active.isPresent()) {
			IssuedSession takeover = sessions.issueMiniProgramTakeover(user.getId(), active.get().getId());
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号已在其他设备登录，确认后可接管",
				Map.of("takeoverToken", takeover.token()));
		}
		try { return sessions.issueMiniProgram(user.getId()); }
		catch (DuplicateKeyException exception) {
			String activeId = sessions.active(user.getId(), com.recording.platform.identity.model.SessionType.MINIPROGRAM)
				.map(session -> session.getId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号登录状态已变化，请重试"));
			IssuedSession takeover = sessions.issueMiniProgramTakeover(user.getId(), activeId);
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IN_USE", "账号已在其他设备登录，确认后可接管",
				Map.of("takeoverToken", takeover.token()));
		}
	}
}

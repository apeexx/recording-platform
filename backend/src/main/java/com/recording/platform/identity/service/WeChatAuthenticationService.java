package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.identity.wechat.WeChatClient;
import com.recording.platform.identity.wechat.WeChatIdentity;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeChatAuthenticationService {
	private final UserStore users;
	private final SessionService sessions;
	private final WeChatClient weChatClient;
	private final Clock clock;

	public WeChatAuthenticationService(
		UserStore users,
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
		UserAccount user = users.findByWechatIdentity(identity.appId(), identity.openId())
			.orElseGet(() -> createCollector(identity));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "账号已停用");
		}
		IssuedSession issued = sessions.issueMiniProgram(user.getId());
		return new MiniProgramLoginResult(issued.token(), issued.session().getId(), user);
	}

	public UserAccount setName(String userId, String name) {
		if (!StringUtils.hasText(name) || name.trim().length() > 64) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NAME", "姓名不能为空且不能超过 64 个字符");
		}
		UserAccount user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() != UserRole.COLLECTOR || user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTOR_REQUIRED", "仅录音人员可以设置姓名");
		}
		return users.updateCollectorNameIfActive(userId, name.trim(), Instant.now(clock))
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
	}

	private UserAccount createCollector(WeChatIdentity identity) {
		Instant now = Instant.now(clock);
		UserAccount user = new UserAccount();
		user.setInternalUserNo(UserNumbers.next());
		user.setRole(UserRole.COLLECTOR);
		user.setStatus(UserStatus.ACTIVE);
		user.setFirstPasswordChangeRequired(false);
		user.setWechatAppId(identity.appId());
		user.setWechatOpenId(identity.openId());
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		return users.save(user);
	}
}

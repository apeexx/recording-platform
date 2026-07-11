package com.recording.platform.identity;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.BcryptPasswordPolicy;
import com.recording.platform.identity.store.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialAdminInitializer implements ApplicationRunner {
	private final UserStore users;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final String username;
	private final String password;

	public InitialAdminInitializer(
		UserStore users,
		PasswordEncoder passwordEncoder,
		Clock clock,
		@Value("${recording.initial-admin.username:}") String username,
		@Value("${recording.initial-admin.password:}") String password
	) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.username = username;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		run();
	}

	public void run() {
		if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
			return;
		}
		if (users.existsByRole(UserRole.ADMIN)) {
			return;
		}
		if (!BcryptPasswordPolicy.isValidForEncoding(password)) {
			throw new IllegalStateException("首管理员初始化密码不符合安全要求");
		}
		Instant now = Instant.now(clock);
		UserAccount admin = new UserAccount();
		admin.setId("initial-admin");
		admin.setInternalUserNo("USR-" + UUID.randomUUID().toString().replace("-", "")
			.substring(0, 12).toUpperCase(Locale.ROOT));
		admin.setUsername(username.trim().toLowerCase(Locale.ROOT));
		admin.setName("首管理员");
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		admin.setFirstPasswordChangeRequired(true);
		admin.setCreatedAt(now);
		admin.setUpdatedAt(now);
		try {
			users.save(admin);
		} catch (DuplicateKeyException ignored) {
			if (!users.existsByRole(UserRole.ADMIN)) {
				throw new IllegalStateException("首管理员初始化失败：用户名或内部编号冲突");
			}
		}
	}
}

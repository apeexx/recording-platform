package com.recording.platform.identity.service;

import com.recording.platform.identity.model.MiniProgramUser;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class CollectorProfilePolicy {
	private static final Pattern ACCOUNT = Pattern.compile("^[1-9][0-9]{5,11}$");

	private CollectorProfilePolicy() { }

	public static boolean isValidAccount(String account) {
		return account != null && ACCOUNT.matcher(account.trim()).matches();
	}

	public static boolean isComplete(MiniProgramUser user) {
		return user != null
			&& StringUtils.hasText(user.getName());
	}
}

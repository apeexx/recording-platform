package com.recording.platform.identity.service;

import com.recording.platform.identity.model.UserAccount;

public record MiniProgramLoginResult(String token, String sessionId, UserAccount user) {
}

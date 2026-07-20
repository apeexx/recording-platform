package com.recording.platform.identity.service;

import com.recording.platform.identity.model.MiniProgramUser;

public record MiniProgramLoginResult(String token, String sessionId, MiniProgramUser user) {
}

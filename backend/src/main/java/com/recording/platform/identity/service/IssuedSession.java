package com.recording.platform.identity.service;

import com.recording.platform.identity.model.SessionRecord;

public record IssuedSession(String token, SessionRecord session) {
}

package com.recording.platform.task.model;

import com.recording.platform.identity.model.UserRole;
import java.time.Instant;

public record CurrentDiscard(
	String reason,
	String actorUserId,
	String actorName,
	UserRole actorRole,
	Instant discardedAt
) { }

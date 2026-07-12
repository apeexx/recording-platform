package com.recording.platform.operation.store;

import java.time.Instant;

public record OperationEntry(
	String itemId,
	String actorUserId,
	String actorUsername,
	String content,
	Instant occurredAt
) { }

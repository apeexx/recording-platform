package com.recording.platform.task.store;

import java.time.Instant;

public record UpdateTaskItemReferencesMutation(
	String itemId,
	long expectedRevision,
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl,
	String referenceAudioMediaId,
	String referenceVideoMediaId,
	String operationId,
	String actorUserId,
	String actorUsername,
	Instant occurredAt
) { }

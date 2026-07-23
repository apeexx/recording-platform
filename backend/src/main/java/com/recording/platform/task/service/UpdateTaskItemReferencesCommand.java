package com.recording.platform.task.service;

public record UpdateTaskItemReferencesCommand(
	long expectedRevision,
	String referenceText,
	String referenceAudioUrl,
	String referenceVideoUrl
) { }

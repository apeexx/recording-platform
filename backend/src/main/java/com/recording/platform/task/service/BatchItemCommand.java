package com.recording.platform.task.service;

public record BatchItemCommand(String itemId, long expectedRevision, String collectorId) { }

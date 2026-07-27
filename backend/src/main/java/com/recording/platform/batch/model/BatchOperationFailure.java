package com.recording.platform.batch.model;

public record BatchOperationFailure(String itemId, String code, String message) { }

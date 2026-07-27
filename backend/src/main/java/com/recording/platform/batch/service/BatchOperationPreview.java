package com.recording.platform.batch.service;

import com.recording.platform.batch.model.BatchOperationAction;
import java.util.Map;

public record BatchOperationPreview(long selectedCount, Map<BatchOperationAction, Long> applicableCounts) { }

package com.recording.platform.batch.service;

import com.recording.platform.batch.model.BatchOperationSource;
import java.util.Set;

public record BatchOperationSelection(
	String taskId,
	BatchOperationSource source,
	Set<String> excludedItemIds
) { }

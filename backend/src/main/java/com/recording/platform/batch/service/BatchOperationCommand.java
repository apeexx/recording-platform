package com.recording.platform.batch.service;

import com.recording.platform.batch.model.BatchOperationAction;
import com.recording.platform.task.model.TaskItemStatus;

public record BatchOperationCommand(
	String operationId,
	BatchOperationAction action,
	BatchOperationSelection selection,
	TaskItemStatus targetStatus,
	String reviewerId
) { }

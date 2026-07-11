package com.recording.platform.task.service;

public record CreateTaskCommand(
	String taskCode,
	String platformId,
	String name,
	String description,
	TaskVersionSpec version
) {
}

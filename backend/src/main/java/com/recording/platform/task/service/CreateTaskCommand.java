package com.recording.platform.task.service;

public record CreateTaskCommand(
	String name,
	String description,
	TaskConfigurationSpec configuration
) {
}

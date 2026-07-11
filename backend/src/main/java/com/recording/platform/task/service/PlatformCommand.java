package com.recording.platform.task.service;

public record PlatformCommand(String code, String name, String description, Boolean active) {
}

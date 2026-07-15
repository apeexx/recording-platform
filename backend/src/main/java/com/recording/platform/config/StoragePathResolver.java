package com.recording.platform.config;

import java.nio.file.Path;

public final class StoragePathResolver {
	private StoragePathResolver() { }

	public static Path resolve(String configuredPath) {
		return resolve(configuredPath, Path.of(System.getProperty("user.dir")));
	}

	static Path resolve(String configuredPath, Path workingDirectory) {
		Path configured = Path.of(configuredPath);
		if (configured.isAbsolute()) {
			return configured.normalize();
		}
		Path working = workingDirectory.toAbsolutePath().normalize();
		Path repository = "backend".equalsIgnoreCase(working.getFileName().toString())
			&& working.getParent() != null ? working.getParent() : working;
		return repository.resolve(configured).normalize();
	}
}

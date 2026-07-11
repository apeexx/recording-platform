package com.recording.platform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
	private static final String PROPERTY_SOURCE_NAME = "recordingPlatformDotEnv";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> values = new LinkedHashMap<>();
		Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		Path projectRoot = "backend".equalsIgnoreCase(userDir.getFileName().toString()) && userDir.getParent() != null
			? userDir.getParent()
			: userDir;
		read(projectRoot.resolve(".env"), values);
		if (!projectRoot.equals(userDir)) {
			read(userDir.resolve(".env"), values);
		}
		if (!values.isEmpty()) {
			environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
		}
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	private void read(Path path, Map<String, Object> values) {
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(path)) {
				String trimmed = line.trim();
				if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
					continue;
				}
				String[] parts = trimmed.split("=", 2);
				String key = parts[0].trim();
				String value = unquote(parts[1].trim());
				if (!key.isBlank()) {
					values.putIfAbsent(key, value);
				}
			}
		} catch (IOException ignored) {
			// Configuration consumers will use safe defaults or report missing values.
		}
	}

	private String unquote(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}
}

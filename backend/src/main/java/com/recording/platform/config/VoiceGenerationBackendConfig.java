package com.recording.platform.config;

import com.recording.platform.voice.VoiceGenerationStorage;
import com.recording.platform.voice.integration.DefaultMiniMaxVoiceClient;
import com.recording.platform.voice.integration.MiniMaxSettings;
import com.recording.platform.voice.integration.MiniMaxVoiceClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class VoiceGenerationBackendConfig {
	private static final String DEFAULT_MINIMAX_BASE_URL = "https://api.minimax.io";
	private static final String DEFAULT_STORAGE_DIR = "backend/storage/voice-generation";

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	MiniMaxSettings miniMaxSettings(Environment environment) {
		Map<String, String> localEnv = readLocalEnv();
		return new MiniMaxSettings(
			resolveSetting(environment, localEnv, "MINIMAX_API_KEY", ""),
			resolveSetting(environment, localEnv, "MINIMAX_API_BASE_URL", DEFAULT_MINIMAX_BASE_URL)
		);
	}

	@Bean
	MiniMaxVoiceClient miniMaxVoiceClient(MiniMaxSettings settings) {
		return new DefaultMiniMaxVoiceClient(settings);
	}

	@Bean
	VoiceGenerationStorage voiceGenerationStorage(Environment environment) {
		Map<String, String> localEnv = readLocalEnv();
		String configuredPath = resolveSetting(
			environment,
			localEnv,
			"VOICE_GENERATION_STORAGE_DIR",
			DEFAULT_STORAGE_DIR
		);
		Path storageDir = Path.of(configuredPath);
		if (!storageDir.isAbsolute()) {
			storageDir = projectRoot().resolve(storageDir);
		}
		return new VoiceGenerationStorage(storageDir.normalize());
	}

	private String resolveSetting(
		Environment environment,
		Map<String, String> localEnv,
		String key,
		String defaultValue
	) {
		String environmentValue = environment.getProperty(key);
		if (StringUtils.hasText(environmentValue)) {
			return environmentValue;
		}
		return localEnv.getOrDefault(key, defaultValue);
	}

	private Map<String, String> readLocalEnv() {
		Map<String, String> values = new HashMap<>();
		for (Path path : new Path[] {projectRoot().resolve(".env"), Path.of(System.getProperty("user.dir")).resolve(".env")}) {
			if (!Files.exists(path)) {
				continue;
			}
			try {
				for (String line : Files.readAllLines(path)) {
					String trimmed = line.trim();
					if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
						continue;
					}
					String[] parts = trimmed.split("=", 2);
					values.putIfAbsent(parts[0].trim(), parts[1].trim());
				}
			} catch (IOException ignored) {
				return values;
			}
		}
		return values;
	}

	private Path projectRoot() {
		Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		if ("backend".equalsIgnoreCase(userDir.getFileName().toString()) && userDir.getParent() != null) {
			return userDir.getParent();
		}
		return userDir;
	}
}

package com.recording.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.voice.integration.MiniMaxSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class VoiceGenerationBackendConfigTests {

	@Test
	void defaultsMiniMaxBaseUrlToChinaOpenPlatformHost() throws Exception {
		String originalUserDir = System.getProperty("user.dir");
		Path tempDir = Files.createTempDirectory("recording-platform-config-test");
		System.setProperty("user.dir", tempDir.toString());
		try {
			MiniMaxSettings settings = new VoiceGenerationBackendConfig().miniMaxSettings(new MockEnvironment());

			assertThat(settings.baseUrl()).isEqualTo("https://api.minimaxi.com");
		} finally {
			System.setProperty("user.dir", originalUserDir);
		}
	}
}

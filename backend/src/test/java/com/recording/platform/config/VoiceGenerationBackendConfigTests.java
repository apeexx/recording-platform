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

	@Test
	void applicationPropertiesDoesNotRequirePersistentDatabaseForVoiceGenerationTrial() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).doesNotContain("spring.datasource");
		assertThat(properties).doesNotContain("spring.jpa");
		assertThat(properties).doesNotContain("spring.flyway");
		assertThat(properties).doesNotContain("spring.data.mongodb");
	}

	@Test
	void applicationPropertiesAllowsMiniMaxCloneAudioUploadSize() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).contains("spring.servlet.multipart.max-file-size=20MB");
		assertThat(properties).contains("spring.servlet.multipart.max-request-size=21MB");
	}
}

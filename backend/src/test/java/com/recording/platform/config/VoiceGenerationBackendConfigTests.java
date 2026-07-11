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
	void applicationPropertiesConfiguresMongoPersistenceWithoutDependingOnADeveloperMachine() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).contains(
			"spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/recording_platform}",
			"spring.data.mongodb.auto-index-creation=true"
		);
	}

	@Test
	void applicationPropertiesAllowsLargeRecordingUploadsWhileCloneKeepsItsBusinessLimit() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).contains("spring.servlet.multipart.max-file-size=100MB");
		assertThat(properties).contains("spring.servlet.multipart.max-request-size=105MB");
		assertThat(properties).contains("recording.storage-dir=${RECORDING_STORAGE_DIR:backend/storage/recordings}");
	}
}

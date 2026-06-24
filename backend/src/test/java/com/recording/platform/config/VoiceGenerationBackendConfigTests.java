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
	void applicationPropertiesDefaultsToPostgreSqlDatasource() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));

		assertThat(properties).contains("spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/recording_platform}");
		assertThat(properties).contains("spring.datasource.username=${SPRING_DATASOURCE_USERNAME:recording_platform}");
		assertThat(properties).contains("spring.jpa.hibernate.ddl-auto=validate");
		assertThat(properties).contains("spring.flyway.enabled=true");
		assertThat(properties).doesNotContain("spring.data.mongodb");
	}

	@Test
	void flywayMigrationCreatesVoiceGenerationTables() throws Exception {
		String migration = Files.readString(Path.of("src/main/resources/db/migration/V1__create_voice_generation_tables.sql"));

		assertThat(migration).contains("CREATE TABLE IF NOT EXISTS voice_generation_records");
		assertThat(migration).contains("CREATE TABLE IF NOT EXISTS voice_generation_configs");
		assertThat(migration).contains("CREATE INDEX IF NOT EXISTS idx_voice_generation_records_created_at");
		assertThat(migration).contains("CHECK (mode IN ('PREVIEW', 'SYNTHESIZE', 'CLONE'))");
		assertThat(migration).contains("CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))");
	}
}

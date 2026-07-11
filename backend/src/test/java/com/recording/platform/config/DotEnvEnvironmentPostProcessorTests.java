package com.recording.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.RecordingPlatformBackendApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DotEnvEnvironmentPostProcessorTests {

	@Test
	void loadsRootDotEnvForSpringPlaceholdersWithoutOverridingRealEnvironmentValues() throws Exception {
		String originalUserDir = System.getProperty("user.dir");
		Path root = Files.createTempDirectory("recording-platform-dotenv");
		Path backend = Files.createDirectories(root.resolve("backend"));
		Files.writeString(root.resolve(".env"), """
			MONGODB_URI=mongodb://localhost:27018/dotenv_test
			WECHAT_APP_ID=dotenv-app
			INITIAL_ADMIN_PASSWORD=dotenv-password
			""");
		System.setProperty("user.dir", backend.toString());
		try {
			StandardEnvironment environment = new StandardEnvironment();
			environment.getPropertySources().addFirst(new MapPropertySource(
				"real-environment",
				Map.of("WECHAT_APP_ID", "system-app")
			));

			new DotEnvEnvironmentPostProcessor().postProcessEnvironment(
				environment,
				new SpringApplication(RecordingPlatformBackendApplication.class)
			);

			assertThat(environment.getProperty("MONGODB_URI"))
				.isEqualTo("mongodb://localhost:27018/dotenv_test");
			assertThat(environment.getProperty("WECHAT_APP_ID")).isEqualTo("system-app");
			assertThat(environment.getProperty("INITIAL_ADMIN_PASSWORD")).isEqualTo("dotenv-password");
		} finally {
			System.setProperty("user.dir", originalUserDir);
		}
	}

	@Test
	void registersThePostProcessorWithoutWritingAnySecretValuesToConfigurationFiles() throws Exception {
		String factories = Files.readString(Path.of("src/main/resources/META-INF/spring.factories"));

		assertThat(factories).contains(DotEnvEnvironmentPostProcessor.class.getName());
		assertThat(factories).doesNotContain("dotenv-password");
	}
}

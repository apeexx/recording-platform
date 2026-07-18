package com.recording.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;

class LocalDataResetRunnerTests {
	@Test
	void securityConfigurationIsSkippedForTheNonWebResetProcess() {
		ConditionalOnWebApplication condition = SecurityConfig.class.getAnnotation(ConditionalOnWebApplication.class);

		assertThat(condition).isNotNull();
		assertThat(condition.type()).isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
	}

	@Test
	void resetProcessExitsOnlyAfterTheAdminInitializerOrder() {
		Order resetOrder = LocalDataResetRunner.class.getAnnotation(Order.class);
		Order exitOrder = LocalDataResetExitRunner.class.getAnnotation(Order.class);

		assertThat(resetOrder.value()).isLessThan(0);
		assertThat(exitOrder.value()).isGreaterThan(0);
	}

	@Test
	void acceptsANarrowRuntimeStorageDirectory() {
		Path working = Path.of(System.getProperty("user.dir"));
		Path repository = "backend".equalsIgnoreCase(working.getFileName().toString())
			? working.getParent() : working;
		Path storage = repository.resolve("backend/storage/recordings");

		assertThat(LocalDataResetRunner.validateStorageRoot(storage))
			.isEqualTo(storage.toAbsolutePath().normalize());
	}

	@Test
	void rejectsHomeAndWorkingDirectories() {
		assertThatThrownBy(() -> LocalDataResetRunner.validateStorageRoot(
			Path.of(System.getProperty("user.home"))
		)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> LocalDataResetRunner.validateStorageRoot(
			Path.of(System.getProperty("user.dir"))
		)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsSourceDirectoriesEvenWhenTheyAreDeepEnough() {
		Path source = Path.of(System.getProperty("user.dir"), "backend", "src", "main");

		assertThatThrownBy(() -> LocalDataResetRunner.validateStorageRoot(source))
			.isInstanceOf(IllegalStateException.class);
	}
}

package com.recording.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoragePathResolverTests {
	@TempDir
	Path tempDir;

	@Test
	void resolvesBackendPrefixedStorageAgainstRepositoryRoot() {
		Path repository = tempDir.resolve("recording-platform").toAbsolutePath();
		Path backend = repository.resolve("backend");

		assertThat(StoragePathResolver.resolve("backend/storage/recordings", backend))
			.isEqualTo(repository.resolve("backend/storage/recordings"));
	}

	@Test
	void resolvesRelativeStorageAgainstCurrentRepositoryDirectory() {
		Path repository = tempDir.resolve("recording-platform").toAbsolutePath();

		assertThat(StoragePathResolver.resolve("backend/storage/recordings", repository))
			.isEqualTo(repository.resolve("backend/storage/recordings"));
	}

	@Test
	void preservesAbsoluteStoragePath() {
		Path absolute = tempDir.resolve("external-recordings").toAbsolutePath();

		assertThat(StoragePathResolver.resolve(absolute.toString(), tempDir))
			.isEqualTo(absolute.normalize());
	}
}

package com.recording.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.MongoTemplate;

class ReadinessServiceTests {
	@TempDir Path temp;

	@Test
	void reportsOnlySanitizedComponentStatesWhenReady() throws Exception {
		MongoTemplate mongo = mock(MongoTemplate.class);
		ReadinessService.Readiness status = new ReadinessService(mongo, temp.toString()).check();
		assertThat(status.overall()).isEqualTo("UP");
		assertThat(status.mongo()).isEqualTo("UP");
		assertThat(status.storage()).isEqualTo("UP");
		assertThat(Files.list(temp)).isEmpty();
	}

	@Test
	void failuresReturnDownWithoutPathsOrExceptionDetails() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		doThrow(new IllegalStateException("sensitive-internal-detail"))
			.when(mongo).executeCommand(any(Document.class));
		Path unavailable = temp.resolve("file-not-directory");
		try { Files.writeString(unavailable, "x"); } catch (Exception exception) { throw new AssertionError(exception); }

		ReadinessService.Readiness status = new ReadinessService(mongo, unavailable.toString()).check();
		assertThat(status.overall()).isEqualTo("DOWN");
		assertThat(status.mongo()).isEqualTo("DOWN");
		assertThat(status.storage()).isEqualTo("DOWN");
		assertThat(status.toString()).doesNotContain("sensitive-internal-detail", temp.toString());
	}
}

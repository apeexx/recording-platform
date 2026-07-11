package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.service.PlatformService;
import com.recording.platform.task.store.PlatformStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformServiceTests {
	@Test
	void referencedPlatformCannotBeDeleted() {
		PlatformStore platforms = org.mockito.Mockito.mock(PlatformStore.class);
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		PlatformRecord platform = new PlatformRecord();
		platform.setId("platform-1");
		when(platforms.findById("platform-1")).thenReturn(Optional.of(platform));
		when(tasks.existsByPlatformId("platform-1")).thenReturn(true);
		PlatformService service = new PlatformService(
			platforms,
			tasks,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);

		assertThatThrownBy(() -> service.delete("platform-1"))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(409);
				assertThat(exception.getCode()).isEqualTo("PLATFORM_IN_USE");
			});
		verify(platforms, never()).deleteById("platform-1");
	}
}

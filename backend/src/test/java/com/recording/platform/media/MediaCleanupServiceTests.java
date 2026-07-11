package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MediaCleanupServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void failedCleanupRemainsPendingAndStartupRecoveryRetriesIt() {
		InMemoryMediaCleanupJobStore jobs = new InMemoryMediaCleanupJobStore();
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		doThrow(new IllegalStateException("simulated backup delete failure"))
			.doNothing()
			.when(storage).delete("temp/backups/old.wav");
		MediaCleanupService service = new MediaCleanupService(jobs, storage, assets, Runnable::run, CLOCK);

		service.scheduleAndTry(
			"item-1",
			"submit-1",
			List.of("temp/backups/old.wav"),
			List.of("old-media")
		);

		MediaCleanupJob pending = jobs.findByItemIdAndOperationId("item-1", "submit-1").orElseThrow();
		assertThat(pending.getStatus()).isEqualTo(MediaCleanupStatus.PENDING);
		assertThat(pending.getAttempt()).isEqualTo(1);
		assertThat(pending.getLastErrorSummary()).doesNotContain("temp/backups");

		service.recoverPending();

		MediaCleanupJob completed = jobs.findByItemIdAndOperationId("item-1", "submit-1").orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(MediaCleanupStatus.COMPLETED);
		assertThat(completed.getAttempt()).isEqualTo(2);
		assertThat(completed.getCompletedAt()).isNotNull();
		verify(storage, times(2)).delete("temp/backups/old.wav");
		verify(assets, times(2)).deleteById("old-media");
	}

	@Test
	void startupRecoveryIsSubmittedWithoutBlockingTheApplicationReadyThread() {
		InMemoryMediaCleanupJobStore jobs = new InMemoryMediaCleanupJobStore();
		RecordingMediaStorage storage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		AtomicReference<Runnable> queued = new AtomicReference<>();
		MediaCleanupService service = new MediaCleanupService(jobs, storage, assets, queued::set, CLOCK);

		service.scheduleStartupRecovery();

		assertThat(queued.get()).isNotNull();
		queued.get().run();
		assertThat(jobs.findPending()).isEmpty();
	}
}

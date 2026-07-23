package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaKind;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RemoteUrlPolicy;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskConfiguration;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskItemCreationServiceTests {
	private TaskItemCreationService service;
	private TaskStore tasks;
	private TaskItemStore items;
	private SafeRemoteMediaDownloader downloader;
	private MediaAssetStore assets;
	private RecordingMediaStorage cleanupStorage;
	private TaskConfiguration configuration;
	private PlatformPrincipal admin;

	@BeforeEach
	void setUp() {
		tasks = org.mockito.Mockito.mock(TaskStore.class);
		items = org.mockito.Mockito.mock(TaskItemStore.class);
		downloader = org.mockito.Mockito.mock(SafeRemoteMediaDownloader.class);
		assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setTaskCode("T000001");
		task.setLifecycle(TaskLifecycle.RUNNING);
		configuration = new TaskConfiguration();
		configuration.setReferenceTypes(Set.of(ReferenceType.TEXT, ReferenceType.AUDIO, ReferenceType.VIDEO));
		task.setConfiguration(configuration);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(tasks.nextItemSequence("task-1")).thenReturn(1L);
		when(items.findByTaskIdAndCreationOperationId(any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		when(assets.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		service = new TaskItemCreationService(
			tasks, items, downloader, assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		admin = new PlatformPrincipal("session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	@Test
	void itemRequiresAtLeastOneReference() {
		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand(null, null, null), "add-1", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED");
		});
	}

	@Test
	void importIgnoresColumnsNotEnabledByTaskAndRejectsRowsEmptyAfterFiltering() {
		configuration.setReferenceTypes(Set.of(ReferenceType.AUDIO));
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		when(downloader.download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "T000001-0000001"
		)).thenReturn(audio);

		TaskItem created = service.addImported(
			"task-1",
			new AddTaskItemCommand("应忽略", "https://cdn.example.com/a.wav", "https://cdn.example.com/ignored.mp4"),
			"import-1",
			admin
		);

		assertThat(created.getReferenceText()).isNull();
		assertThat(created.getReferenceVideoUrl()).isNull();
		assertThat(created.getReferenceAudioUrl()).isEqualTo("https://cdn.example.com/a.wav");
		assertThatThrownBy(() -> service.addImported(
			"task-1", new AddTaskItemCommand("仅有禁用文字", null, null), "import-2", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED")
		);
	}

	@Test
	void itemUsesTaskConfigurationAtomicSequenceAndLocallyDownloadedReferences() {
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		MediaAsset video = asset("video-1", MediaKind.REFERENCE_VIDEO);
		when(downloader.download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "T000001-0000001"
		)).thenReturn(audio);
		when(downloader.download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "T000001-0000001"
		)).thenReturn(video);

		TaskItem created = service.add(
			"task-1",
			new AddTaskItemCommand("请朗读", "https://cdn.example.com/a.wav", "https://cdn.example.com/v.mp4"),
			"add-1",
			admin
		);

		assertThat(created.getTaskId()).isEqualTo("task-1");
		assertThat(created.getSequence()).isEqualTo(1);
		assertThat(created.getItemCode()).isEqualTo("T000001-0000001");
		assertThat(created.getReferenceAudioMediaId()).isEqualTo("audio-1");
		assertThat(created.getReferenceVideoMediaId()).isEqualTo("video-1");
		assertThat(created.getReferenceAudioUrl()).isEqualTo("https://cdn.example.com/a.wav");
		assertThat(created.getReferenceVideoUrl()).isEqualTo("https://cdn.example.com/v.mp4");
		assertThat(created.getStatus()).isEqualTo(TaskItemStatus.AVAILABLE);
		assertThat(created.getOperations()).singleElement().satisfies((operation) -> {
			assertThat(operation.getOperationId()).isEqualTo("add-1");
			assertThat(operation.getActorUsername()).isEqualTo("admin");
			assertThat(operation.getContent()).contains("新增了任务条目");
		});
		ArgumentCaptor<MediaAsset> attached = ArgumentCaptor.forClass(MediaAsset.class);
		org.mockito.Mockito.verify(assets, org.mockito.Mockito.times(2)).save(attached.capture());
		assertThat(attached.getAllValues()).allSatisfy((asset) -> assertThat(asset.getItemId()).isEqualTo(created.getId()));
	}

	@Test
	void itemSequenceStopsAtOneMillion() {
		when(tasks.nextItemSequence("task-1")).thenReturn(1_000_001L);

		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand("请朗读", null, null), "add-overflow", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(409);
			assertThat(exception.getCode()).isEqualTo("ITEM_CODE_EXHAUSTED");
		});
	}

	@Test
	void videoDownloadFailureRemovesTheSavedAudioFileAndMetadata() {
		useCleanupAwareDownloader();
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		doReturn(audio).when(downloader).download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "T000001-0000001"
		);
		doThrow(new ApiException(
			org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
			"REMOTE_MEDIA_DOWNLOAD_FAILED",
			"视频下载失败"
		)).when(downloader).download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "T000001-0000001"
		);

		assertThatThrownBy(() -> service.add(
			"task-1",
			new AddTaskItemCommand(null, "https://cdn.example.com/a.wav", "https://cdn.example.com/v.mp4"),
			"add-video-failure",
			admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("REMOTE_MEDIA_DOWNLOAD_FAILED")
		);

		verify(cleanupStorage).delete(audio.getRelativePath());
		verify(assets).deleteById("audio-1");
	}

	@Test
	void itemSaveFailureRemovesEveryDownloadedFileAndMetadataDocument() {
		useCleanupAwareDownloader();
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		MediaAsset video = asset("video-1", MediaKind.REFERENCE_VIDEO);
		doReturn(audio).when(downloader).download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "T000001-0000001"
		);
		doReturn(video).when(downloader).download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "T000001-0000001"
		);
		doThrow(new IllegalStateException("simulated item save failure")).when(items).save(any());

		assertThatThrownBy(() -> service.add(
			"task-1",
			new AddTaskItemCommand(null, "https://cdn.example.com/a.wav", "https://cdn.example.com/v.mp4"),
			"add-save-failure",
			admin
		)).isInstanceOf(IllegalStateException.class).hasMessageContaining("item save failure");

		verify(cleanupStorage).delete(audio.getRelativePath());
		verify(cleanupStorage).delete(video.getRelativePath());
		verify(assets).deleteById("audio-1");
		verify(assets).deleteById("video-1");
	}

	@Test
	void cleanupFailureIsReportedInsteadOfBeingSilentlyDiscarded() {
		useCleanupAwareDownloader();
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		doReturn(audio).when(downloader).download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "T000001-0000001"
		);
		doThrow(new ApiException(
			org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
			"REMOTE_MEDIA_DOWNLOAD_FAILED",
			"视频下载失败"
		)).when(downloader).download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "T000001-0000001"
		);
		doThrow(new IllegalStateException("simulated file cleanup failure"))
			.when(cleanupStorage).delete(audio.getRelativePath());
		doThrow(new IllegalStateException("simulated metadata cleanup failure"))
			.when(assets).deleteById("audio-1");

		assertThatThrownBy(() -> service.add(
			"task-1",
			new AddTaskItemCommand(null, "https://cdn.example.com/a.wav", "https://cdn.example.com/v.mp4"),
			"add-cleanup-failure",
			admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(500);
			assertThat(exception.getCode()).isEqualTo("REFERENCE_MEDIA_CLEANUP_FAILED");
			assertThat(exception.getSuppressed()).hasSize(2);
		});
		verify(cleanupStorage).delete(audio.getRelativePath());
		verify(assets).deleteById("audio-1");
	}

	private void useCleanupAwareDownloader() {
		cleanupStorage = org.mockito.Mockito.mock(RecordingMediaStorage.class);
		downloader = org.mockito.Mockito.spy(new SafeRemoteMediaDownloader(
			org.mockito.Mockito.mock(RemoteUrlPolicy.class),
			org.mockito.Mockito.mock(RemoteHttpTransport.class),
			cleanupStorage,
			assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
			Duration.ofSeconds(1),
			0
		));
		service = new TaskItemCreationService(
			tasks,
			items,
			downloader,
			assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
	}

	private MediaAsset asset(String id, MediaKind kind) {
		MediaAsset asset = new MediaAsset();
		asset.setId(id);
		asset.setKind(kind);
		asset.setRelativePath("references/task-1/T000001-0000001/" + id);
		return asset;
	}
}

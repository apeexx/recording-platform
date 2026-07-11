package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaKind;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ReferenceType;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import com.recording.platform.task.store.TaskVersionStore;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskItemCreationServiceTests {
	private TaskItemCreationService service;
	private TaskItemStore items;
	private SafeRemoteMediaDownloader downloader;
	private MediaAssetStore assets;
	private TaskVersion version;
	private PlatformPrincipal admin;

	@BeforeEach
	void setUp() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		TaskVersionStore versions = org.mockito.Mockito.mock(TaskVersionStore.class);
		items = org.mockito.Mockito.mock(TaskItemStore.class);
		downloader = org.mockito.Mockito.mock(SafeRemoteMediaDownloader.class);
		assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setTaskCode("TASK-001");
		task.setLifecycle(TaskLifecycle.RUNNING);
		task.setCurrentVersionId("version-2");
		task.setCurrentVersionNumber(2);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		when(tasks.nextItemSequence("task-1")).thenReturn(1L);
		version = new TaskVersion();
		version.setId("version-2");
		version.setVersionNumber(2);
		version.setPublished(true);
		version.setReferenceTypes(Set.of(ReferenceType.TEXT, ReferenceType.AUDIO, ReferenceType.VIDEO));
		when(versions.findById("version-2")).thenReturn(Optional.of(version));
		when(items.findByTaskIdAndCreationOperationId(any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		when(assets.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		service = new TaskItemCreationService(
			tasks, versions, items, downloader, assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		admin = new PlatformPrincipal("session-1", "admin-1", "admin", "管理员", UserRole.ADMIN, SessionType.WEB, false);
	}

	@Test
	void itemRequiresAtLeastOneReference() {
		assertThatThrownBy(() -> service.add(
			"task-1", new AddTaskItemCommand(null, null, null, null), "add-1", admin
		)).isInstanceOfSatisfying(ApiException.class, (exception) -> {
			assertThat(exception.getStatus().value()).isEqualTo(422);
			assertThat(exception.getCode()).isEqualTo("ITEM_REFERENCE_REQUIRED");
		});
	}

	@Test
	void itemUsesCurrentVersionAtomicSequenceAndLocallyDownloadedReferences() {
		MediaAsset audio = asset("audio-1", MediaKind.REFERENCE_AUDIO);
		MediaAsset video = asset("video-1", MediaKind.REFERENCE_VIDEO);
		when(downloader.download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "I000001"
		)).thenReturn(audio);
		when(downloader.download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "I000001"
		)).thenReturn(video);

		TaskItem created = service.add(
			"task-1",
			new AddTaskItemCommand(
				"external-1", "请朗读", "https://cdn.example.com/a.wav", "https://cdn.example.com/v.mp4"
			),
			"add-1",
			admin
		);

		assertThat(created.getTaskVersionId()).isEqualTo("version-2");
		assertThat(created.getTaskVersionNumber()).isEqualTo(2);
		assertThat(created.getSequence()).isEqualTo(1);
		assertThat(created.getItemCode()).isEqualTo("I000001");
		assertThat(created.getReferenceAudioMediaId()).isEqualTo("audio-1");
		assertThat(created.getReferenceVideoMediaId()).isEqualTo("video-1");
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

	private MediaAsset asset(String id, MediaKind kind) {
		MediaAsset asset = new MediaAsset();
		asset.setId(id);
		asset.setKind(kind);
		asset.setRelativePath("references/task-1/I000001/" + id);
		return asset;
	}
}

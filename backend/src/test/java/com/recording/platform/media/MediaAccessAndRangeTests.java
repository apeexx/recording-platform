package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MediaAccessAndRangeTests {
	@TempDir
	Path tempDir;

	@Test
	void collectorCanReadOwnedPendingMediaAfterGrantRevocationButUnrelatedCollectorCannot() throws Exception {
		Path path = tempDir.resolve("recordings/TASK-001/I000001/current.wav");
		Files.createDirectories(path.getParent());
		Files.writeString(path, "0123456789");
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskGrantStore grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		MediaAsset asset = new MediaAsset();
		asset.setId("media-1");
		asset.setTaskId("task-1");
		asset.setItemId("item-1");
		asset.setKind(MediaKind.RECORDING);
		asset.setRelativePath("recordings/TASK-001/I000001/current.wav");
		asset.setContentType("audio/wav");
		asset.setSizeBytes(10);
		when(assets.findById("media-1")).thenReturn(Optional.of(asset));
		TaskItem item = new TaskItem();
		item.setId("item-1");
		item.setCollectorId("collector-1");
		item.setStatus(TaskItemStatus.RECORDING_PENDING);
		when(items.findById("item-1")).thenReturn(Optional.of(item));
		TaskGrant unrelatedGrant = new TaskGrant();
		unrelatedGrant.setStatus(GrantStatus.ACTIVE);
		when(grants.findActive("task-1", "collector-2")).thenReturn(Optional.of(unrelatedGrant));
		MediaAccessService service = new MediaAccessService(
			assets, items, grants, new RecordingMediaStorage(tempDir)
		);

		ReadableMedia readable = service.open("media-1", principal("collector-1", UserRole.COLLECTOR));
		assertThat(readable.length()).isEqualTo(10);
		assertThatThrownBy(() -> service.open("media-1", principal("collector-2", UserRole.COLLECTOR)))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN)
			);
	}

	@Test
	void ownedReferenceMediaAndRangeReturnsOnlyTheRequestedRegion() throws Exception {
		Path path = tempDir.resolve("references/task-1/audio/reference.wav");
		Files.createDirectories(path.getParent());
		Files.writeString(path, "0123456789");
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		TaskItemStore items = org.mockito.Mockito.mock(TaskItemStore.class);
		TaskGrantStore grants = org.mockito.Mockito.mock(TaskGrantStore.class);
		MediaAsset asset = new MediaAsset();
		asset.setId("media-ref");
		asset.setTaskId("task-1");
		asset.setItemId("item-ref");
		asset.setKind(MediaKind.REFERENCE_AUDIO);
		asset.setRelativePath("references/task-1/audio/reference.wav");
		asset.setContentType("audio/wav");
		asset.setSizeBytes(10);
		when(assets.findById("media-ref")).thenReturn(Optional.of(asset));
		TaskItem item = new TaskItem();
		item.setId("item-ref");
		item.setCollectorId("collector-1");
		when(items.findById("item-ref")).thenReturn(Optional.of(item));
		MediaAccessService access = new MediaAccessService(
			assets, items, grants, new RecordingMediaStorage(tempDir)
		);
		MediaController controller = new MediaController(access);

		ResponseEntity<?> response = controller.read(
			"media-ref", "bytes=2-5", principal("collector-1", UserRole.COLLECTOR)
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
		assertThat(response.getBody()).isInstanceOfSatisfying(ResourceRegion.class, (region) -> {
			assertThat(region.getPosition()).isEqualTo(2);
			assertThat(region.getCount()).isEqualTo(4);
			assertThat(region.getResource()).isInstanceOf(FileSystemResource.class);
		});
		assertThatThrownBy(() -> controller.read(
			"media-ref", "bytes=0-1,4-5", principal("collector-1", UserRole.COLLECTOR)
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("INVALID_RANGE")
		);
	}

	private PlatformPrincipal principal(String userId, UserRole role) {
		SessionType type = role == UserRole.COLLECTOR ? SessionType.MINIPROGRAM : SessionType.WEB;
		return new PlatformPrincipal("session-1", userId, userId, userId, role, type, false);
	}
}

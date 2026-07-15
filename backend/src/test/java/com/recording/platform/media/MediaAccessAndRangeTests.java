package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.api.GlobalApiExceptionHandler;
import com.recording.platform.api.RequestIdFilter;
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
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

		ResponseEntity<StreamingResponseBody> response = controller.read(
			"media-ref", "bytes=2-5", principal("collector-1", UserRole.COLLECTOR)
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		response.getBody().writeTo(output);
		assertThat(output.toString()).isEqualTo("2345");
		assertThatThrownBy(() -> controller.read(
			"media-ref", "bytes=0-1,4-5", principal("collector-1", UserRole.COLLECTOR)
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("INVALID_RANGE")
		);
	}

	@Test
	void httpRangeRequestWritesMp3BytesWithPartialContentHeaders() throws Exception {
		byte[] mp3Bytes = new byte[] {
			(byte) 0xff, (byte) 0xf3, 0x68, (byte) 0xc4, 0x00, 0x01, 0x02, 0x03
		};
		Path path = tempDir.resolve("recordings/TASK-001/I000001/current.mp3");
		Files.createDirectories(path.getParent());
		Files.write(path, mp3Bytes);
		MediaAccessService access = org.mockito.Mockito.mock(MediaAccessService.class);
		when(access.open(org.mockito.ArgumentMatchers.eq("media-mp3"), any()))
			.thenReturn(new ReadableMedia(path, "audio/mpeg", mp3Bytes.length));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
			new MediaHttpTestController(new MediaController(access))
		)
			.setControllerAdvice(new GlobalApiExceptionHandler())
			.addFilters(new RequestIdFilter())
			.build();

		MvcResult initial = mockMvc.perform(get("/api/media/media-mp3").header(HttpHeaders.RANGE, "bytes=0-"))
			.andExpect(result -> assertThat(result.getResolvedException()).isNull())
			.andExpect(status().isPartialContent())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
			.andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-7/8"))
			.andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "8"))
			.andReturn();
		MvcResult completed = initial.getRequest().isAsyncStarted()
			? mockMvc.perform(asyncDispatch(initial)).andReturn()
			: initial;

		assertThat(completed.getResponse().getContentAsByteArray()).containsExactly(mp3Bytes);
	}

	@RestController
	private static class MediaHttpTestController {
		private final MediaController delegate;

		private MediaHttpTestController(MediaController delegate) {
			this.delegate = delegate;
		}

		@GetMapping("/api/media/{mediaId}")
		ResponseEntity<StreamingResponseBody> read(
			@PathVariable String mediaId,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
		) {
			return delegate.read(
				mediaId,
				rangeHeader,
				new PlatformPrincipal(
					"session-1", "admin-1", "A000001", "管理员", UserRole.ADMIN, SessionType.WEB, false
				)
			);
		}
	}

	private PlatformPrincipal principal(String userId, UserRole role) {
		SessionType type = role == UserRole.COLLECTOR ? SessionType.MINIPROGRAM : SessionType.WEB;
		return new PlatformPrincipal("session-1", userId, userId, userId, role, type, false);
	}
}

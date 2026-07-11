package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RemoteUrlPolicy;
import com.recording.platform.media.ResolvedRemote;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeRemoteMediaDownloaderTests {
	@TempDir
	Path tempDir;

	@Test
	void dangerousRedirectIsBlockedBeforeTheSecondRequest() throws Exception {
		RemoteUrlPolicy policy = new RemoteUrlPolicy(false, (host) ->
			List.of(InetAddress.getByName(host.equals("cdn.example.com") ? "93.184.216.34" : "127.0.0.1"))
		);
		Queue<RemoteHttpResponse> responses = new ArrayDeque<>();
		responses.add(response(302, Map.of("location", List.of("https://localhost/private.wav")), new byte[0]));
		CountingTransport transport = new CountingTransport(responses);
		SafeRemoteMediaDownloader downloader = downloader(policy, transport);

		assertThatThrownBy(() -> downloader.download(
			URI.create("https://cdn.example.com/a.wav"), RemoteMediaType.AUDIO, "task-1", "I000001"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("REMOTE_HOST_BLOCKED")
		);
		assertThat(transport.requests).isEqualTo(1);
	}

	@Test
	void declaredOversizedVideoIsRejectedWithoutWritingTheBody() throws Exception {
		RemoteUrlPolicy policy = publicPolicy();
		RemoteHttpResponse response = response(
			200,
			Map.of("content-length", List.of(Long.toString(SafeRemoteMediaDownloader.MAX_VIDEO_BYTES + 1))),
			new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}
		);
		CountingTransport transport = new CountingTransport(new ArrayDeque<>(List.of(response)));

		assertThatThrownBy(() -> downloader(policy, transport).download(
			URI.create("https://cdn.example.com/v.mp4"), RemoteMediaType.VIDEO, "task-1", "I000001"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("REMOTE_MEDIA_TOO_LARGE")
		);
	}

	@Test
	void successfulDownloadStoresOnlyHostnameStatusAndRelativePath() throws Exception {
		RemoteUrlPolicy policy = publicPolicy();
		CountingTransport transport = new CountingTransport(new ArrayDeque<>(List.of(response(
			200,
			Map.of("content-type", List.of("audio/wav")),
			wav()
		))));
		MediaAsset asset = downloader(policy, transport).download(
			URI.create("https://cdn.example.com/a.wav?signature=secret"),
			RemoteMediaType.AUDIO,
			"task-1",
			"I000001"
		);

		assertThat(asset.getSourceHostname()).isEqualTo("cdn.example.com");
		assertThat(asset.getSourceStatus()).isEqualTo(200);
		assertThat(asset.getRelativePath()).startsWith("references/task-1/I000001/")
			.doesNotContain("signature").doesNotContain("secret");
	}

	@Test
	void eachRequestHopUsesExactlyOneValidatedDnsResolution() throws Exception {
		AtomicInteger resolutions = new AtomicInteger();
		RemoteUrlPolicy policy = new RemoteUrlPolicy(false, (host) -> {
			resolutions.incrementAndGet();
			return List.of(InetAddress.getByName("93.184.216.34"));
		});
		CountingTransport transport = new CountingTransport(new ArrayDeque<>(List.of(response(
			200,
			Map.of("content-type", List.of("audio/wav")),
			wav()
		))));

		downloader(policy, transport).download(
			URI.create("https://cdn.example.com/a.wav"),
			RemoteMediaType.AUDIO,
			"task-1",
			"I000001"
		);

		assertThat(resolutions).hasValue(1);
	}

	private SafeRemoteMediaDownloader downloader(RemoteUrlPolicy policy, RemoteHttpTransport transport) {
		MediaAssetStore assets = org.mockito.Mockito.mock(MediaAssetStore.class);
		when(assets.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		return new SafeRemoteMediaDownloader(
			policy,
			transport,
			new RecordingMediaStorage(tempDir),
			assets,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC),
			Duration.ofSeconds(5),
			3
		);
	}

	private RemoteUrlPolicy publicPolicy() throws Exception {
		return new RemoteUrlPolicy(false, (host) -> List.of(InetAddress.getByName("93.184.216.34")));
	}

	private RemoteHttpResponse response(int status, Map<String, List<String>> headers, byte[] body) {
		return new RemoteHttpResponse(status, headers, new ByteArrayInputStream(body));
	}

	private byte[] wav() {
		int sampleRate = 16000;
		int dataLength = 32000;
		ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
		header.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + dataLength).put(new byte[] {'W', 'A', 'V', 'E'});
		header.put(new byte[] {'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1);
		header.putInt(sampleRate).putInt(32000).putShort((short) 2).putShort((short) 16);
		header.put(new byte[] {'d', 'a', 't', 'a'}).putInt(dataLength);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.writeBytes(header.array());
		output.writeBytes(new byte[dataLength]);
		return output.toByteArray();
	}

	private static final class CountingTransport implements RemoteHttpTransport {
		private final Queue<RemoteHttpResponse> responses;
		private int requests;
		private CountingTransport(Queue<RemoteHttpResponse> responses) { this.responses = responses; }
		@Override public RemoteHttpResponse get(ResolvedRemote remote, Duration timeout) {
			requests++;
			return responses.remove();
		}
	}
}

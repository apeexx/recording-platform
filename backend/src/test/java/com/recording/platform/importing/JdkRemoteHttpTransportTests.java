package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.ResolvedRemote;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JdkRemoteHttpTransportTests {
	@Test
	void connectsToTheValidatedAddressWithoutResolvingTheOriginalHostnameAgain() throws Exception {
		byte[] responseBytes = (
			"HTTP/1.1 200 OK\r\n"
				+ "Content-Length: 5\r\n"
				+ "X-Origin: pinned\r\n"
				+ "Connection: close\r\n\r\nhello"
		).getBytes(StandardCharsets.US_ASCII);
		try (ServerSocket server = loopbackServer()) {
			CompletableFuture<String> request = serveOnce(server, responseBytes);
			URI uri = URI.create("http://must-not-resolve.invalid:" + server.getLocalPort() + "/media/a.wav?part=1");
			ResolvedRemote remote = new ResolvedRemote(
				uri,
				"must-not-resolve.invalid",
				List.of(server.getInetAddress())
			);

			try (RemoteHttpResponse response = new JdkRemoteHttpTransport().get(remote, Duration.ofSeconds(2))) {
				assertThat(response.status()).isEqualTo(200);
				assertThat(response.firstHeader("x-origin")).isEqualTo("pinned");
				assertThat(response.body().readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.US_ASCII));
			}

			assertThat(request.get(2, TimeUnit.SECONDS))
				.startsWith("GET /media/a.wav?part=1 HTTP/1.1\r\n")
				.contains("Host: must-not-resolve.invalid:" + server.getLocalPort() + "\r\n");
		}
	}

	@Test
	void decodesChunkedBodiesWithoutBufferingTheWholeResponse() throws Exception {
		byte[] responseBytes = (
			"HTTP/1.1 200 OK\r\n"
				+ "Transfer-Encoding: chunked\r\n"
				+ "Connection: close\r\n\r\n"
				+ "4\r\nWiki\r\n"
				+ "5;extension=value\r\npedia\r\n"
				+ "0\r\nTrailer: ignored\r\n\r\n"
		).getBytes(StandardCharsets.US_ASCII);
		try (ServerSocket server = loopbackServer()) {
			serveOnce(server, responseBytes);
			URI uri = URI.create("http://chunked.invalid:" + server.getLocalPort() + "/asset");
			ResolvedRemote remote = new ResolvedRemote(uri, "chunked.invalid", List.of(server.getInetAddress()));

			try (RemoteHttpResponse response = new JdkRemoteHttpTransport().get(remote, Duration.ofSeconds(2))) {
				assertThat(response.body().readAllBytes()).isEqualTo("Wikipedia".getBytes(StandardCharsets.US_ASCII));
			}
		}
	}

	@Test
	void totalTimeoutStopsAResponseThatTricklesBytesBeforeEachIdleTimeout() throws Exception {
		byte[] responseBytes = (
			"HTTP/1.1 204 No Content\r\n"
				+ "Connection: close\r\n\r\n"
		).getBytes(StandardCharsets.US_ASCII);
		try (ServerSocket server = loopbackServer()) {
			serveSlowly(server, responseBytes, 20);
			URI uri = URI.create("http://slow.invalid:" + server.getLocalPort() + "/asset");
			ResolvedRemote remote = new ResolvedRemote(uri, "slow.invalid", List.of(server.getInetAddress()));

			assertThatThrownBy(() -> new JdkRemoteHttpTransport().get(remote, Duration.ofMillis(100)))
				.isInstanceOfSatisfying(ApiException.class, (exception) ->
					assertThat(exception.getCode()).isEqualTo("REMOTE_MEDIA_UNAVAILABLE")
				);
		}
	}

	@Test
	void fixedLengthBodyHonorsZeroLengthInputStreamReadsAtEndOfBody() throws Exception {
		byte[] responseBytes = (
			"HTTP/1.1 200 OK\r\n"
				+ "Content-Length: 0\r\n"
				+ "Connection: close\r\n\r\n"
		).getBytes(StandardCharsets.US_ASCII);
		try (ServerSocket server = loopbackServer()) {
			serveOnce(server, responseBytes);
			URI uri = URI.create("http://empty.invalid:" + server.getLocalPort() + "/asset");
			ResolvedRemote remote = new ResolvedRemote(uri, "empty.invalid", List.of(server.getInetAddress()));

			try (RemoteHttpResponse response = new JdkRemoteHttpTransport().get(remote, Duration.ofSeconds(2))) {
				assertThat(response.body().read(new byte[1], 0, 0)).isZero();
				assertThat(response.body().read()).isEqualTo(-1);
			}
		}
	}

	private ServerSocket loopbackServer() throws Exception {
		return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
	}

	private CompletableFuture<String> serveOnce(ServerSocket server, byte[] response) {
		return CompletableFuture.supplyAsync(() -> {
			try (Socket socket = server.accept()) {
				String request = readHeaders(socket.getInputStream());
				socket.getOutputStream().write(response);
				socket.getOutputStream().flush();
				return request;
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
	}

	private CompletableFuture<Void> serveSlowly(ServerSocket server, byte[] response, long delayMillis) {
		return CompletableFuture.runAsync(() -> {
			try (Socket socket = server.accept()) {
				readHeaders(socket.getInputStream());
				for (byte value : response) {
					socket.getOutputStream().write(value);
					socket.getOutputStream().flush();
					Thread.sleep(delayMillis);
				}
			} catch (Exception ignored) {
				// The expected timeout closes the client socket while this fixture is still writing.
			}
		});
	}

	private String readHeaders(InputStream input) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int matched = 0;
		while (matched < 4) {
			int value = input.read();
			if (value < 0) throw new IllegalStateException("request ended before headers");
			output.write(value);
			matched = switch (matched) {
				case 0 -> value == '\r' ? 1 : 0;
				case 1 -> value == '\n' ? 2 : 0;
				case 2 -> value == '\r' ? 3 : 0;
				default -> value == '\n' ? 4 : 0;
			};
		}
		return output.toString(StandardCharsets.US_ASCII);
	}
}

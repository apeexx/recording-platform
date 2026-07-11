package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.ResolvedRemote;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JdkRemoteHttpTransport implements RemoteHttpTransport {
	private static final int MAX_HEADER_BYTES = 64 * 1024;
	private static final int MAX_HEADER_COUNT = 100;
	private static final int MAX_CHUNK_LINE_BYTES = 8 * 1024;
	private final SSLSocketFactory sslSocketFactory;

	public JdkRemoteHttpTransport() {
		this((SSLSocketFactory) SSLSocketFactory.getDefault());
	}

	JdkRemoteHttpTransport(SSLSocketFactory sslSocketFactory) {
		this.sslSocketFactory = Objects.requireNonNull(sslSocketFactory);
	}

	@Override
	public RemoteHttpResponse get(ResolvedRemote remote, Duration timeout) {
		validate(remote);
		Socket socket = null;
		try {
			long timeoutNanos = positiveTimeout(timeout);
			long startedAt = System.nanoTime();
			socket = connect(remote, startedAt, timeoutNanos);
			writeRequest(socket.getOutputStream(), remote.uri(), remote.hostname());
			InputStream input = new DeadlineInputStream(
				socket.getInputStream(),
				socket,
				startedAt,
				timeoutNanos
			);
			RemoteHttpResponse response = readResponse(socket, input);
			socket = null;
			return response;
		} catch (IOException exception) {
			throw unavailable();
		} finally {
			closeQuietly(socket);
		}
	}

	private Socket connect(ResolvedRemote remote, long startedAt, long timeoutNanos) throws IOException {
		IOException lastFailure = null;
		int port = port(remote.uri());
		for (InetAddress address : remote.addresses()) {
			Socket plain = new Socket();
			try {
				plain.connect(new InetSocketAddress(address, port), remainingMillis(startedAt, timeoutNanos));
				plain.setSoTimeout(remainingMillis(startedAt, timeoutNanos));
				if ("http".equalsIgnoreCase(remote.uri().getScheme())) return plain;

				SSLSocket secure = (SSLSocket) sslSocketFactory.createSocket(
					plain,
					remote.hostname(),
					port,
					true
				);
				SSLParameters parameters = secure.getSSLParameters();
				parameters.setEndpointIdentificationAlgorithm("HTTPS");
				parameters.setServerNames(List.of(new SNIHostName(remote.hostname())));
				secure.setSSLParameters(parameters);
				secure.setSoTimeout(remainingMillis(startedAt, timeoutNanos));
				secure.startHandshake();
				return secure;
			} catch (IOException exception) {
				lastFailure = exception;
				closeQuietly(plain);
			}
		}
		if (lastFailure != null) throw lastFailure;
		throw new IOException("no resolved address");
	}

	private void writeRequest(OutputStream output, URI uri, String hostname) throws IOException {
		String path = uri.getRawPath();
		if (path == null || path.isEmpty()) path = "/";
		if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
		String host = hostname.contains(":") ? "[" + hostname + "]" : hostname;
		int port = port(uri);
		if (port != defaultPort(uri.getScheme())) host += ":" + port;
		String request = "GET " + path + " HTTP/1.1\r\n"
			+ "Host: " + host + "\r\n"
			+ "Accept: */*\r\n"
			+ "User-Agent: recording-platform/1.0\r\n"
			+ "Connection: close\r\n\r\n";
		output.write(request.getBytes(StandardCharsets.US_ASCII));
		output.flush();
	}

	private RemoteHttpResponse readResponse(Socket socket, InputStream input) throws IOException {
		ParsedHead head;
		do {
			head = readHead(input);
		} while (head.status() >= 100 && head.status() < 200 && head.status() != 101);

		InputStream body;
		if (head.status() == 101 || head.status() == 204 || head.status() == 304) {
			body = new EmptySocketInputStream(socket);
		} else if (isChunked(head.headers())) {
			body = new ChunkedSocketInputStream(input, socket);
		} else {
			Long contentLength = contentLength(head.headers());
			body = contentLength == null
				? new SocketClosingInputStream(input, socket)
				: new FixedLengthSocketInputStream(input, socket, contentLength);
		}
		return new RemoteHttpResponse(head.status(), head.headers(), body);
	}

	private ParsedHead readHead(InputStream input) throws IOException {
		HeaderBudget budget = new HeaderBudget();
		String statusLine = readLine(input, budget, MAX_HEADER_BYTES);
		String[] statusParts = statusLine.split(" ", 3);
		if (statusParts.length < 2 || !("HTTP/1.1".equals(statusParts[0]) || "HTTP/1.0".equals(statusParts[0]))) {
			throw new IOException("invalid HTTP status line");
		}
		int status;
		try {
			status = Integer.parseInt(statusParts[1]);
		} catch (NumberFormatException exception) {
			throw new IOException("invalid HTTP status", exception);
		}
		if (status < 100 || status > 999) throw new IOException("invalid HTTP status");

		Map<String, List<String>> headers = new LinkedHashMap<>();
		int count = 0;
		while (true) {
			String line = readLine(input, budget, MAX_HEADER_BYTES);
			if (line.isEmpty()) break;
			if (++count > MAX_HEADER_COUNT) throw new IOException("too many HTTP headers");
			int separator = line.indexOf(':');
			if (separator <= 0) throw new IOException("invalid HTTP header");
			String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
			if (!isToken(name)) throw new IOException("invalid HTTP header name");
			String value = line.substring(separator + 1).trim();
			headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
		}
		return new ParsedHead(status, headers);
	}

	private boolean isChunked(Map<String, List<String>> headers) throws IOException {
		List<String> values = headers.get("transfer-encoding");
		if (values == null) return false;
		List<String> codings = new ArrayList<>();
		for (String value : values) {
			for (String coding : value.split(",")) {
				String normalized = coding.trim().toLowerCase(Locale.ROOT);
				if (!normalized.isEmpty()) codings.add(normalized);
			}
		}
		if (codings.size() != 1 || !"chunked".equals(codings.get(0))) {
			throw new IOException("unsupported transfer encoding");
		}
		return true;
	}

	private Long contentLength(Map<String, List<String>> headers) throws IOException {
		List<String> values = headers.get("content-length");
		if (values == null) return null;
		Long expected = null;
		for (String value : values) {
			for (String part : value.split(",")) {
				long parsed;
				try {
					parsed = Long.parseLong(part.trim());
				} catch (NumberFormatException exception) {
					throw new IOException("invalid content length", exception);
				}
				if (parsed < 0 || (expected != null && expected != parsed)) {
					throw new IOException("conflicting content length");
				}
				expected = parsed;
			}
		}
		return expected;
	}

	private String readLine(InputStream input, HeaderBudget budget, int maximumBytes) throws IOException {
		StringBuilder line = new StringBuilder();
		boolean carriageReturn = false;
		while (true) {
			int value = input.read();
			if (value < 0) throw new EOFException("HTTP line ended unexpectedly");
			budget.add(1, maximumBytes);
			if (carriageReturn) {
				if (value != '\n') throw new IOException("invalid HTTP line ending");
				return line.toString();
			}
			if (value == '\r') {
				carriageReturn = true;
			} else if (value == '\n' || value > 0x7f) {
				throw new IOException("invalid HTTP header byte");
			} else {
				line.append((char) value);
			}
		}
	}

	private boolean isToken(String value) {
		if (value.isEmpty()) return false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (!(Character.isLetterOrDigit(character) || "!#$%&'*+-.^_`|~".indexOf(character) >= 0)) {
				return false;
			}
		}
		return true;
	}

	private void validate(ResolvedRemote remote) {
		if (remote == null || remote.uri() == null || remote.hostname() == null || remote.addresses().isEmpty()) {
			throw unavailable();
		}
		String scheme = remote.uri().getScheme();
		String uriHostname = remote.uri().getHost();
		if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
			|| uriHostname == null || !uriHostname.equalsIgnoreCase(remote.hostname())) {
			throw unavailable();
		}
	}

	private int port(URI uri) {
		return uri.getPort() >= 0 ? uri.getPort() : defaultPort(uri.getScheme());
	}

	private int defaultPort(String scheme) {
		return "https".equalsIgnoreCase(scheme) ? 443 : 80;
	}

	private long positiveTimeout(Duration timeout) throws SocketTimeoutException {
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new SocketTimeoutException("invalid timeout");
		}
		try {
			return timeout.toNanos();
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}

	private int remainingMillis(long startedAt, long timeoutNanos) throws SocketTimeoutException {
		long elapsed = System.nanoTime() - startedAt;
		long remaining = timeoutNanos == Long.MAX_VALUE ? Long.MAX_VALUE : timeoutNanos - elapsed;
		if (remaining <= 0) throw new SocketTimeoutException("remote request timed out");
		long millis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
		return (int) Math.min(millis, Integer.MAX_VALUE);
	}

	private void closeQuietly(Socket socket) {
		if (socket == null) return;
		try {
			socket.close();
		} catch (IOException ignored) {
			// Best effort cleanup after transport failure.
		}
	}

	private ApiException unavailable() {
		return new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"REMOTE_MEDIA_UNAVAILABLE",
			"远程媒体暂时无法下载"
		);
	}

	private record ParsedHead(int status, Map<String, List<String>> headers) {}

	private static final class HeaderBudget {
		private int bytes;
		private void add(int amount, int maximum) throws IOException {
			bytes += amount;
			if (bytes > maximum) throw new IOException("HTTP headers are too large");
		}
	}

	private static class SocketClosingInputStream extends FilterInputStream {
		private final Socket socket;
		private boolean closed;

		private SocketClosingInputStream(InputStream input, Socket socket) {
			super(input);
			this.socket = socket;
		}

		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			try {
				super.close();
			} finally {
				socket.close();
			}
		}
	}

	private final class DeadlineInputStream extends FilterInputStream {
		private final Socket socket;
		private final long startedAt;
		private final long timeoutNanos;

		private DeadlineInputStream(InputStream input, Socket socket, long startedAt, long timeoutNanos) {
			super(input);
			this.socket = socket;
			this.startedAt = startedAt;
			this.timeoutNanos = timeoutNanos;
		}

		@Override
		public int read() throws IOException {
			socket.setSoTimeout(remainingMillis(startedAt, timeoutNanos));
			return super.read();
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			socket.setSoTimeout(remainingMillis(startedAt, timeoutNanos));
			return super.read(bytes, offset, length);
		}
	}

	private static final class FixedLengthSocketInputStream extends SocketClosingInputStream {
		private long remaining;

		private FixedLengthSocketInputStream(InputStream input, Socket socket, long remaining) {
			super(input, socket);
			this.remaining = remaining;
		}

		@Override
		public int read() throws IOException {
			if (remaining == 0) return -1;
			int value = super.read();
			if (value < 0) throw new EOFException("response body ended before Content-Length");
			remaining--;
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			Objects.checkFromIndexSize(offset, length, bytes.length);
			if (length == 0) return 0;
			if (remaining == 0) return -1;
			int allowed = (int) Math.min(length, remaining);
			int read = super.read(bytes, offset, allowed);
			if (read < 0) throw new EOFException("response body ended before Content-Length");
			remaining -= read;
			return read;
		}
	}

	private final class ChunkedSocketInputStream extends InputStream {
		private final InputStream input;
		private final Socket socket;
		private long remaining;
		private boolean chunkTerminatorExpected;
		private boolean finished;
		private boolean closed;

		private ChunkedSocketInputStream(InputStream input, Socket socket) {
			this.input = input;
			this.socket = socket;
		}

		@Override
		public int read() throws IOException {
			if (!ensureChunk()) return -1;
			int value = input.read();
			if (value < 0) throw new EOFException("chunk ended unexpectedly");
			remaining--;
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			Objects.checkFromIndexSize(offset, length, bytes.length);
			if (length == 0) return 0;
			if (!ensureChunk()) return -1;
			int allowed = (int) Math.min(length, remaining);
			int read = input.read(bytes, offset, allowed);
			if (read < 0) throw new EOFException("chunk ended unexpectedly");
			remaining -= read;
			return read;
		}

		private boolean ensureChunk() throws IOException {
			if (finished) return false;
			if (remaining > 0) return true;
			if (chunkTerminatorExpected) readExpectedCrlf();
			HeaderBudget budget = new HeaderBudget();
			String line = readLine(input, budget, MAX_CHUNK_LINE_BYTES);
			int extension = line.indexOf(';');
			String sizeText = (extension >= 0 ? line.substring(0, extension) : line).trim();
			try {
				remaining = Long.parseUnsignedLong(sizeText, 16);
			} catch (NumberFormatException exception) {
				throw new IOException("invalid chunk size", exception);
			}
			if (remaining == 0) {
				readTrailers();
				finished = true;
				return false;
			}
			chunkTerminatorExpected = true;
			return true;
		}

		private void readExpectedCrlf() throws IOException {
			if (input.read() != '\r' || input.read() != '\n') {
				throw new IOException("invalid chunk terminator");
			}
			chunkTerminatorExpected = false;
		}

		private void readTrailers() throws IOException {
			HeaderBudget budget = new HeaderBudget();
			int count = 0;
			while (true) {
				String trailer = readLine(input, budget, MAX_HEADER_BYTES);
				if (trailer.isEmpty()) return;
				if (++count > MAX_HEADER_COUNT || trailer.indexOf(':') <= 0) {
					throw new IOException("invalid chunk trailer");
				}
			}
		}

		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			try {
				input.close();
			} finally {
				socket.close();
			}
		}
	}

	private static final class EmptySocketInputStream extends InputStream {
		private final Socket socket;
		private boolean closed;

		private EmptySocketInputStream(Socket socket) {
			this.socket = socket;
		}

		@Override
		public int read() {
			return -1;
		}

		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			socket.close();
		}
	}
}

package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaKind;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RemoteUrlPolicy;
import com.recording.platform.media.ResolvedRemote;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SafeRemoteMediaDownloader {
	public static final long MAX_AUDIO_BYTES = 100L * 1024 * 1024;
	public static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
	private final RemoteUrlPolicy policy;
	private final RemoteHttpTransport transport;
	private final RecordingMediaStorage storage;
	private final MediaAssetStore assets;
	private final Clock clock;
	private final Duration timeout;
	private final int maxRedirects;

	@Autowired
	public SafeRemoteMediaDownloader(
		RemoteUrlPolicy policy,
		RemoteHttpTransport transport,
		RecordingMediaStorage storage,
		MediaAssetStore assets,
		Clock clock,
		@Value("${recording.remote-media.timeout-seconds:15}") long timeoutSeconds,
		@Value("${recording.remote-media.max-redirects:3}") int maxRedirects
	) {
		this(policy, transport, storage, assets, clock, Duration.ofSeconds(Math.max(timeoutSeconds, 1)), maxRedirects);
	}

	public SafeRemoteMediaDownloader(
		RemoteUrlPolicy policy,
		RemoteHttpTransport transport,
		RecordingMediaStorage storage,
		MediaAssetStore assets,
		Clock clock,
		Duration timeout,
		int maxRedirects
	) {
		this.policy = policy;
		this.transport = transport;
		this.storage = storage;
		this.assets = assets;
		this.clock = clock;
		this.timeout = timeout;
		this.maxRedirects = Math.max(maxRedirects, 0);
	}

	public MediaAsset download(URI source, RemoteMediaType type, String taskId, String itemCode) {
		URI current = source;
		for (int redirects = 0; redirects <= maxRedirects; redirects++) {
			ResolvedRemote resolved = policy.resolve(current);
			try (RemoteHttpResponse response = transport.get(resolved, timeout)) {
				if (response.status() >= 300 && response.status() < 400) {
					String location = response.firstHeader("location");
					if (location == null || redirects == maxRedirects) {
						throw failure("REMOTE_REDIRECT_BLOCKED", "远程媒体重定向不合法或次数过多");
					}
					current = current.resolve(location);
					continue;
				}
				if (response.status() < 200 || response.status() >= 300) {
					throw failure("REMOTE_MEDIA_DOWNLOAD_FAILED", "远程媒体下载失败，状态码 " + response.status());
				}
				return persist(response, resolved, type, taskId, itemCode, current);
			} catch (IOException exception) {
				throw failure("REMOTE_MEDIA_UNAVAILABLE", "远程媒体读取失败");
			}
		}
		throw failure("REMOTE_REDIRECT_BLOCKED", "远程媒体重定向次数过多");
	}

	public void delete(MediaAsset asset) {
		if (asset == null) return;
		try {
			storage.delete(asset.getRelativePath());
		} finally {
			if (asset.getId() != null) assets.deleteById(asset.getId());
		}
	}

	private MediaAsset persist(
		RemoteHttpResponse response,
		ResolvedRemote remote,
		RemoteMediaType type,
		String taskId,
		String itemCode,
		URI finalUri
	) throws IOException {
		long limit = type == RemoteMediaType.AUDIO ? MAX_AUDIO_BYTES : MAX_VIDEO_BYTES;
		String lengthHeader = response.firstHeader("content-length");
		if (lengthHeader != null) {
			try {
				if (Long.parseLong(lengthHeader) > limit) throw tooLarge();
			} catch (NumberFormatException exception) {
				throw failure("REMOTE_MEDIA_INVALID", "远程媒体长度头不合法");
			}
		}
		String extension = extension(finalUri, type);
		String safeTask = safeSegment(taskId);
		String safeItem = safeSegment(itemCode);
		String id = UUID.randomUUID().toString();
		Path temporary = storage.resolve("temp/downloads/" + id + "." + extension);
		String relative = "references/" + safeTask + "/" + safeItem + "/"
			+ type.name().toLowerCase(Locale.ROOT) + "-" + id + "." + extension;
		Path target = storage.resolve(relative);
		try {
			Files.createDirectories(temporary.getParent());
			long size = copyLimited(response.body(), temporary, limit);
			validateMagic(temporary, type, extension);
			Files.createDirectories(target.getParent());
			move(temporary, target);
			MediaAsset asset = new MediaAsset();
			asset.setId(id);
			asset.setTaskId(taskId);
			asset.setKind(type == RemoteMediaType.AUDIO ? MediaKind.REFERENCE_AUDIO : MediaKind.REFERENCE_VIDEO);
			asset.setRelativePath(relative);
			asset.setContentType(contentType(response.firstHeader("content-type"), type, extension));
			asset.setSizeBytes(size);
			asset.setSourceHostname(remote.hostname());
			asset.setSourceStatus(response.status());
			asset.setCreatedAt(Instant.now(clock));
			return assets.save(asset);
		} catch (RuntimeException | IOException exception) {
			Files.deleteIfExists(temporary);
			Files.deleteIfExists(target);
			throw exception;
		}
	}

	private long copyLimited(InputStream input, Path target, long limit) throws IOException {
		byte[] buffer = new byte[8192];
		long total = 0;
		try (var output = Files.newOutputStream(target)) {
			int read;
			while ((read = input.read(buffer)) >= 0) {
				total += read;
				if (total > limit) throw tooLarge();
				output.write(buffer, 0, read);
			}
		}
		return total;
	}

	private void validateMagic(Path path, RemoteMediaType type, String extension) throws IOException {
		byte[] prefix;
		try (InputStream input = Files.newInputStream(path)) {
			prefix = input.readNBytes(12);
		}
		boolean valid;
		if (type == RemoteMediaType.AUDIO && "wav".equals(extension)) {
			valid = prefix.length >= 12 && ascii(prefix, 0, "RIFF") && ascii(prefix, 8, "WAVE");
		} else if (type == RemoteMediaType.AUDIO) {
			valid = prefix.length >= 3 && ((prefix[0] == 'I' && prefix[1] == 'D' && prefix[2] == '3')
				|| ((prefix[0] & 0xff) == 0xff && (prefix[1] & 0xe0) == 0xe0));
		} else if ("mp4".equals(extension)) {
			valid = prefix.length >= 12 && ascii(prefix, 4, "ftyp");
		} else {
			valid = prefix.length >= 4 && (prefix[0] & 0xff) == 0x1a && (prefix[1] & 0xff) == 0x45
				&& (prefix[2] & 0xff) == 0xdf && (prefix[3] & 0xff) == 0xa3;
		}
		if (!valid) throw failure("REMOTE_MEDIA_INVALID", "远程媒体格式或魔数不合法");
	}

	private String extension(URI uri, RemoteMediaType type) {
		String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
		if (type == RemoteMediaType.AUDIO) {
			if (path.endsWith(".wav")) return "wav";
			if (path.endsWith(".mp3")) return "mp3";
		} else {
			if (path.endsWith(".mp4")) return "mp4";
			if (path.endsWith(".webm")) return "webm";
		}
		throw failure("REMOTE_MEDIA_TYPE_UNSUPPORTED", "远程媒体文件类型不支持");
	}

	private String contentType(String header, RemoteMediaType type, String extension) {
		String normalized = header == null ? "" : header.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if (type == RemoteMediaType.AUDIO && (normalized.equals("audio/wav") || normalized.equals("audio/x-wav")
			|| normalized.equals("audio/mpeg") || normalized.isEmpty())) {
			return "wav".equals(extension) ? "audio/wav" : "audio/mpeg";
		}
		if (type == RemoteMediaType.VIDEO && (normalized.equals("video/mp4") || normalized.equals("video/webm")
			|| normalized.isEmpty())) {
			return "mp4".equals(extension) ? "video/mp4" : "video/webm";
		}
		throw failure("REMOTE_MEDIA_CONTENT_TYPE_INVALID", "远程媒体 Content-Type 不支持");
	}

	private String safeSegment(String value) {
		if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
			throw failure("REMOTE_MEDIA_PATH_INVALID", "远程媒体保存路径不合法");
		}
		return value;
	}

	private void move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private boolean ascii(byte[] bytes, int offset, String value) {
		if (bytes.length < offset + value.length()) return false;
		for (int index = 0; index < value.length(); index++) {
			if (bytes[offset + index] != (byte) value.charAt(index)) return false;
		}
		return true;
	}

	private ApiException tooLarge() {
		return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "REMOTE_MEDIA_TOO_LARGE", "远程媒体超过大小限制");
	}
	private ApiException failure(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}
}

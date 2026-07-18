package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import com.recording.platform.config.StoragePathResolver;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RecordingMediaStorage {
	public static final long MAX_RECORDING_BYTES = 100L * 1024 * 1024;
	private final Path root;
	private final AudioMetadataInspector inspector = new AudioMetadataInspector();

	@Autowired
	public RecordingMediaStorage(@Value("${recording.storage-dir:backend/storage/recording-data}") String root) {
		this(StoragePathResolver.resolve(root));
	}

	public RecordingMediaStorage(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path rootPath() {
		return root;
	}

	public PreparedRecording prepare(
		MultipartFile upload,
		TaskVersion version,
		String taskCode,
		String itemCode
	) {
		if (upload == null || upload.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_REQUIRED", "录音文件不能为空");
		}
		if (upload.getSize() > MAX_RECORDING_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "录音文件不能超过 100MB");
		}
		String safeTaskCode = safeSegment(taskCode);
		String safeItemCode = safeSegment(itemCode);
		String extension = extension(upload.getOriginalFilename());
		if (!extension.equals("wav") && !extension.equals("mp3")) {
			throw invalidAudio("录音文件扩展名只支持 wav 或 mp3");
		}
		Path temporary = resolve("temp/recordings/" + UUID.randomUUID() + "." + extension);
		try {
			Files.createDirectories(temporary.getParent());
			try (InputStream input = upload.getInputStream()) {
				Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.size(temporary) > MAX_RECORDING_BYTES) {
				throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "录音文件不能超过 100MB");
			}
			AudioMetadata metadata = inspector.inspect(temporary, upload.getOriginalFilename());
			validateAgainstVersion(metadata, version);
			String relative = safeTaskCode + "/" + safeItemCode + "." + extension;
			SubmittedRecording recording = new SubmittedRecording(
				UUID.randomUUID().toString(),
				relative,
				metadata.format(),
				Files.size(temporary),
				metadata.sampleRate(),
				metadata.channels(),
				metadata.durationMillis()
			);
			return new PreparedRecording(recording, temporary);
		} catch (ApiException exception) {
			deleteQuietly(temporary);
			throw exception;
		} catch (IOException exception) {
			deleteQuietly(temporary);
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "录音文件暂时无法保存");
		}
	}

	public RecordingReplacement activate(PreparedRecording prepared, String previousRelativePath) {
		Path current = resolve(prepared.recording().relativePath());
		Path previous = previousRelativePath == null ? current : resolve(previousRelativePath);
		Path backup = null;
		try {
			Files.createDirectories(current.getParent());
			if (Files.exists(previous)) {
				backup = resolve("temp/backups/" + UUID.randomUUID() + extensionWithDot(previous));
				Files.createDirectories(backup.getParent());
				atomicMove(previous, backup);
			}
			atomicMove(prepared.temporaryPath(), current);
			return new RecordingReplacement(this, current, previous, backup);
		} catch (IOException exception) {
			deleteQuietly(current);
			if (backup != null && Files.exists(backup)) {
				try { atomicMove(backup, previous); } catch (IOException ignored) { }
			}
			deleteQuietly(prepared.temporaryPath());
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "录音文件暂时无法替换");
		}
	}

	public RecordingRetirement stageRetirement(String relativePath) {
		Path original = resolve(relativePath);
		Path backup = null;
		try {
			if (Files.exists(original)) {
				backup = resolve("temp/backups/" + UUID.randomUUID() + extensionWithDot(original));
				Files.createDirectories(backup.getParent());
				atomicMove(original, backup);
			}
			return new RecordingRetirement(this, original, backup);
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "录音文件暂时无法隔离清理");
		}
	}

	public Path resolve(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			throw traversal();
		}
		Path relative = Path.of(relativePath.replace('\\', '/'));
		if (relative.isAbsolute()) throw traversal();
		Path resolved = root.resolve(relative).normalize();
		if (!resolved.startsWith(root)) throw traversal();
		return resolved;
	}

	public void delete(String relativePath) {
		if (relativePath != null) deleteRequired(resolve(relativePath));
	}

	void deleteRequired(Path path) {
		if (path == null) return;
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_DELETE_FAILED", "媒体文件暂时无法删除");
		}
	}

	void atomicMove(Path source, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			throw new IOException("storage does not support atomic move", exception);
		}
	}

	void deleteQuietly(Path path) {
		if (path == null) return;
		try { Files.deleteIfExists(path); } catch (IOException ignored) { }
	}

	String relative(Path path) {
		if (path == null) return null;
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(root)) throw traversal();
		return root.relativize(normalized).toString().replace('\\', '/');
	}

	private void validateAgainstVersion(AudioMetadata metadata, TaskVersion version) {
		if (version == null || metadata.format() != version.getRecordingFormat()) {
			throw invalid("INVALID_AUDIO_FORMAT", "录音格式不符合任务配置");
		}
		if (metadata.channels() != 1 || metadata.channels() != version.getChannels()) {
			throw invalid("INVALID_AUDIO_CHANNELS", "录音必须为单声道");
		}
		if (version.getSampleRates() == null || !version.getSampleRates().contains(metadata.sampleRate())) {
			throw invalid("INVALID_AUDIO_SAMPLE_RATE", "录音采样率不符合任务配置");
		}
		if (metadata.durationMillis() < version.getMinDurationMillis()
			|| metadata.durationMillis() > version.getMaxDurationMillis()) {
			throw invalid("INVALID_AUDIO_DURATION", "录音时长不符合任务配置");
		}
	}

	private String safeSegment(String value) {
		if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw traversal();
		return value;
	}

	private String extension(String filename) {
		if (filename == null) return "";
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String extensionWithDot(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? ".bak" : name.substring(dot);
	}

	private ApiException invalidAudio(String message) { return invalid("INVALID_AUDIO_FILE", message); }
	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}
	private ApiException traversal() {
		return new ApiException(HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_BLOCKED", "媒体路径不合法");
	}
}

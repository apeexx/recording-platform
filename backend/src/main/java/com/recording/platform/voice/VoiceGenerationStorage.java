package com.recording.platform.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

public class VoiceGenerationStorage {
	private final Path storageDir;

	public VoiceGenerationStorage(Path storageDir) {
		this.storageDir = storageDir.toAbsolutePath().normalize();
	}

	public Path rootPath() {
		return storageDir;
	}

	public Path save(String recordId, String format, byte[] audioBytes) {
		try {
			Files.createDirectories(storageDir);
			String safeFormat = StringUtils.hasText(format) ? format : "mp3";
			Path target = storageDir.resolve(recordId + "." + safeFormat);
			Files.write(target, audioBytes);
			return target;
		} catch (IOException exception) {
			throw new VoiceGenerationException("保存生成音频失败");
		}
	}

	public FileSystemResource load(Path path) {
		if (path == null || !Files.exists(path)) {
			throw new VoiceGenerationException("生成音频不存在或已被清理");
		}
		return new FileSystemResource(path);
	}
}

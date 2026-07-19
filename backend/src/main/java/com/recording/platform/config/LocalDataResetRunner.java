package com.recording.platform.config;

import com.recording.platform.identity.service.BcryptPasswordPolicy;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.voice.VoiceGenerationStorage;
import com.recording.platform.identity.service.CollectorAvatarService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(-1000)
@ConditionalOnProperty(name = "recording.local-reset.enabled", havingValue = "true")
public final class LocalDataResetRunner implements ApplicationRunner {
	static final String REQUIRED_DATABASE = "recording_platform";

	private final MongoTemplate mongo;
	private final RecordingMediaStorage recordings;
	private final VoiceGenerationStorage voices;
	private final CollectorAvatarService avatars;
	private final String initialAdminUsername;
	private final String initialAdminPassword;
	private final String confirmation;

	public LocalDataResetRunner(
		MongoTemplate mongo,
		RecordingMediaStorage recordings,
		VoiceGenerationStorage voices,
		CollectorAvatarService avatars,
		@Value("${recording.initial-admin.username:}") String initialAdminUsername,
		@Value("${recording.initial-admin.password:}") String initialAdminPassword,
		@Value("${recording.local-reset.confirmation:}") String confirmation
	) {
		this.mongo = mongo;
		this.recordings = recordings;
		this.voices = voices;
		this.avatars = avatars;
		this.initialAdminUsername = initialAdminUsername;
		this.initialAdminPassword = initialAdminPassword;
		this.confirmation = confirmation;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		String databaseName = mongo.getDb().getName();
		if (!REQUIRED_DATABASE.equals(databaseName)) {
			throw new IllegalStateException("本地重置已拒绝：数据库名称必须精确为 recording_platform");
		}
		if (!REQUIRED_DATABASE.equals(confirmation)) {
			throw new IllegalStateException("本地重置已拒绝：缺少精确数据库确认词");
		}
		if (!StringUtils.hasText(initialAdminUsername)
			|| !BcryptPasswordPolicy.isValidForEncoding(initialAdminPassword)) {
			throw new IllegalStateException("本地重置已拒绝：必须配置有效的首管理员用户名和密码");
		}

		Path recordingRoot = validateStorageRoot(recordings.rootPath());
		Path voiceRoot = validateStorageRoot(voices.rootPath());
		Path avatarRoot = validateStorageRoot(avatars.rootPath());
		mongo.getDb().drop();
		deleteChildren(recordingRoot);
		if (!voiceRoot.equals(recordingRoot)) {
			deleteChildren(voiceRoot);
		}
		if (!avatarRoot.equals(recordingRoot) && !avatarRoot.equals(voiceRoot)) deleteChildren(avatarRoot);
	}

	static Path validateStorageRoot(Path configured) {
		Path root = configured.toAbsolutePath().normalize();
		Path filesystemRoot = root.getRoot();
		Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
		Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		Path repository = "backend".equalsIgnoreCase(working.getFileName().toString()) && working.getParent() != null
			? working.getParent() : working;
		Path repositoryStorage = repository.resolve("backend/storage").normalize();
		String leaf = root.getFileName() == null ? "" : root.getFileName().toString();
		boolean approvedLeaf = leaf.equalsIgnoreCase("recordings")
			|| leaf.equalsIgnoreCase("recording-data") || leaf.equalsIgnoreCase("voice-generation")
			|| leaf.equalsIgnoreCase("avatars");
		boolean unsafeRepositoryPath = root.startsWith(repository) && !root.startsWith(repositoryStorage);
		boolean containsRepository = repository.startsWith(root);
		if (root.equals(filesystemRoot) || root.equals(userHome) || root.equals(working) || root.getNameCount() < 3
			|| !approvedLeaf || unsafeRepositoryPath || containsRepository) {
			throw new IllegalStateException("本地重置已拒绝：存储目录范围不安全");
		}
		return root;
	}

	private static void deleteChildren(Path root) throws IOException {
		if (!Files.exists(root)) {
			Files.createDirectories(root);
			return;
		}
		try (var children = Files.list(root)) {
			for (Path child : children.toList()) {
				try (var descendants = Files.walk(child)) {
					descendants.sorted(Comparator.reverseOrder()).forEach((path) -> {
						try {
							Files.delete(path);
						} catch (IOException exception) {
							throw new ResetFileException(exception);
						}
					});
				} catch (ResetFileException exception) {
					throw exception.cause;
				}
			}
		}
	}

	private static final class ResetFileException extends RuntimeException {
		private final IOException cause;

		private ResetFileException(IOException cause) {
			this.cause = cause;
		}
	}
}

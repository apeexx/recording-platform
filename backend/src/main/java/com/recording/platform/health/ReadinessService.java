package com.recording.platform.health;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReadinessService {
	private final MongoTemplate mongo;
	private final Path storageRoot;

	public ReadinessService(
		MongoTemplate mongo,
		@Value("${recording.storage-dir:backend/storage/recordings}") String storageRoot
	) {
		this.mongo = mongo;
		this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
	}

	public Readiness check() {
		String mongoState = checkMongo() ? "UP" : "DOWN";
		String storageState = checkStorage() ? "UP" : "DOWN";
		String overall = "UP".equals(mongoState) && "UP".equals(storageState) ? "UP" : "DOWN";
		return new Readiness(overall, mongoState, storageState);
	}

	private boolean checkMongo() {
		try {
			mongo.executeCommand(new Document("ping", 1));
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private boolean checkStorage() {
		Path probe = storageRoot.resolve(".ready-probe-" + UUID.randomUUID());
		try {
			Files.createDirectories(storageRoot);
			Files.createFile(probe);
			return Files.isWritable(probe);
		} catch (Exception exception) {
			return false;
		} finally {
			try { Files.deleteIfExists(probe); } catch (Exception ignored) { }
		}
	}

	public record Readiness(String overall, String mongo, String storage) { }
}

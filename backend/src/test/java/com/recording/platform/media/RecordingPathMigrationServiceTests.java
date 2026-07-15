package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class RecordingPathMigrationServiceTests {
	private static final String OLD_PATH = "recordings/TASK-001/I000001/current.mp3";
	private static final String TARGET_PATH = "TASK-001/I000001.mp3";

	@TempDir
	Path tempDir;

	@Test
	void mapsOnlyStrictLegacyRecordingPaths() {
		RecordingPathMigrationService service = service(mock(MongoTemplate.class));

		assertThat(service.targetPath(OLD_PATH)).isEqualTo(TARGET_PATH);
		assertThatThrownBy(() -> service.targetPath("references/TASK-001/I000001.mp3"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void movesARecordingWithoutChangingItsHashAndReplacesAllPathReferences() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		RecordingPathMigrationService service = service(mongo);
		byte[] audio = "legacy recording bytes".getBytes();
		write(OLD_PATH, audio);
		String originalHash = sha256(storage().resolve(OLD_PATH));

		RecordingPathMigrationResult result = service.migrate();

		assertThat(result).isEqualTo(new RecordingPathMigrationResult(1, 0));
		assertThat(storage().resolve(OLD_PATH)).doesNotExist();
		assertThat(storage().resolve(TARGET_PATH)).hasBinaryContent(audio);
		assertThat(sha256(storage().resolve(TARGET_PATH))).isEqualTo(originalHash);

		ArgumentCaptor<Document> replacements = ArgumentCaptor.forClass(Document.class);
		verify(mongo, times(4)).replace(any(Query.class), replacements.capture(), any(String.class));
		assertThat(replacements.getAllValues())
			.extracting(Document::toJson)
			.allSatisfy(json -> {
				assertThat(json).contains(TARGET_PATH);
				assertThat(json).doesNotContain(OLD_PATH);
			});
	}

	@Test
	void identicalTargetIsDeduplicatedAfterDatabaseReplacement() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		RecordingPathMigrationService service = service(mongo);
		byte[] audio = "same recording bytes".getBytes();
		write(OLD_PATH, audio);
		write(TARGET_PATH, audio);

		RecordingPathMigrationResult result = service.migrate();

		assertThat(result).isEqualTo(new RecordingPathMigrationResult(0, 1));
		assertThat(storage().resolve(OLD_PATH)).doesNotExist();
		assertThat(storage().resolve(TARGET_PATH)).hasBinaryContent(audio);
		verify(mongo, times(4)).replace(any(Query.class), any(Document.class), any(String.class));
	}

	@Test
	void differentTargetHashStopsWithoutChangingFilesOrMongo() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		RecordingPathMigrationService service = service(mongo);
		byte[] oldAudio = "old recording bytes".getBytes();
		byte[] targetAudio = "different target bytes".getBytes();
		write(OLD_PATH, oldAudio);
		write(TARGET_PATH, targetAudio);

		assertThatThrownBy(service::migrate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("conflict");
		assertThat(storage().resolve(OLD_PATH)).hasBinaryContent(oldAudio);
		assertThat(storage().resolve(TARGET_PATH)).hasBinaryContent(targetAudio);
		verify(mongo, never()).replace(any(Query.class), any(Document.class), any(String.class));
	}

	@Test
	void failedConditionalReplaceRestoresUpdatedDocumentsAndMovesFileBack() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		when(mongo.replace(any(Query.class), any(Document.class), eq("task_items")))
			.thenReturn(UpdateResult.acknowledged(0, 0L, null));
		RecordingPathMigrationService service = service(mongo);
		byte[] audio = "rollback recording bytes".getBytes();
		write(OLD_PATH, audio);

		assertThatThrownBy(service::migrate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("document conflict");

		assertThat(storage().resolve(OLD_PATH)).hasBinaryContent(audio);
		assertThat(storage().resolve(TARGET_PATH)).doesNotExist();
		ArgumentCaptor<Document> mediaReplacements = ArgumentCaptor.forClass(Document.class);
		verify(mongo, times(2)).replace(
			any(Query.class), mediaReplacements.capture(), eq("media_assets")
		);
		assertThat(mediaReplacements.getAllValues().get(0).getString("relativePath"))
			.isEqualTo(TARGET_PATH);
		assertThat(mediaReplacements.getAllValues().get(1).getString("relativePath"))
			.isEqualTo(OLD_PATH);
	}

	private RecordingPathMigrationService service(MongoTemplate mongo) {
		return new RecordingPathMigrationService(storage(), mongo);
	}

	private RecordingMediaStorage storage() {
		return new RecordingMediaStorage(tempDir);
	}

	private MongoTemplate mongoWithLegacyDocuments() {
		MongoTemplate mongo = mock(MongoTemplate.class);
		Document media = versioned("media-1")
			.append("kind", "RECORDING")
			.append("relativePath", OLD_PATH);
		Document taskItem = versioned("item-1")
			.append("currentResult", new Document("audio", new Document("relativePath", OLD_PATH)))
			.append("operations", List.of(new Document("resultSnapshot",
				new Document("audio", new Document("relativePath", OLD_PATH)))));
		Document cleanup = versioned("cleanup-1")
			.append("relativePaths", List.of(OLD_PATH, "temp/backups/keep.mp3"));
		Document idempotency = versioned("idem-1")
			.append("responseJson", "{\"audio\":{\"relativePath\":\"" + OLD_PATH + "\"}}");

		when(mongo.find(any(Query.class), eq(Document.class), any(String.class)))
			.thenAnswer(invocation -> switch (invocation.getArgument(2, String.class)) {
				case "media_assets" -> List.of(media);
				case "task_items" -> List.of(taskItem);
				case "media_cleanup_jobs" -> List.of(cleanup);
				case "idempotency_records" -> List.of(idempotency);
				default -> List.of();
			});
		when(mongo.replace(any(Query.class), any(Document.class), any(String.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, null));
		return mongo;
	}

	private Document versioned(String id) {
		return new Document("_id", id).append("version", 4L);
	}

	private void write(String relativePath, byte[] bytes) throws Exception {
		Path path = storage().resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(path)) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;) {
				if (read > 0) digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}

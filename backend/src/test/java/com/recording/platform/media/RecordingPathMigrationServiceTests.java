package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
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
			.allSatisfy(json -> assertThat(json).contains(TARGET_PATH));
	}

	@Test
	void duplicateMediaAssetsWithOneLegacyPathAreMigratedTogetherOnce() throws Exception {
		Document first = legacyMedia("media-1");
		Document second = legacyMedia("media-2");
		MongoTemplate mongo = mongoWithLegacyDocuments(first, second);
		RecordingPathMigrationService service = service(mongo);
		byte[] audio = "shared legacy recording".getBytes();
		write(OLD_PATH, audio);

		RecordingPathMigrationResult result = service.migrate();

		assertThat(result).isEqualTo(new RecordingPathMigrationResult(1, 0));
		assertThat(storage().resolve(OLD_PATH)).doesNotExist();
		assertThat(storage().resolve(TARGET_PATH)).hasBinaryContent(audio);
		ArgumentCaptor<Document> replacements = ArgumentCaptor.forClass(Document.class);
		verify(mongo, times(2)).replace(
			any(Query.class), replacements.capture(), eq("media_assets")
		);
		assertThat(replacements.getAllValues())
			.extracting(document -> document.getString("relativePath"))
			.containsExactly(TARGET_PATH, TARGET_PATH);
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
		byte[] oldAudio = "same-size-old".getBytes();
		byte[] targetAudio = "same-size-new".getBytes();
		assertThat(targetAudio).hasSameSizeAs(oldAudio);
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
	void everyConditionalReplaceUsesTheDocumentIdAndOriginalVersion() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		RecordingPathMigrationService service = service(mongo);
		write(OLD_PATH, "query conditions".getBytes());

		service.migrate();

		ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Document> replacements = ArgumentCaptor.forClass(Document.class);
		verify(mongo, times(4)).replace(
			queries.capture(), replacements.capture(), any(String.class)
		);
		for (int index = 0; index < queries.getAllValues().size(); index++) {
			Document query = queries.getAllValues().get(index).getQueryObject();
			Document replacement = replacements.getAllValues().get(index);
			assertThat(query).containsOnlyKeys("_id", "version");
			assertThat(query.get("_id")).isEqualTo(replacement.get("_id"));
			assertThat(query.get("version")).isEqualTo(4L);
		}
	}

	@Test
	void laterCollectionFailureRestoresEarlierDocumentsInReverseOrder() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		when(mongo.replace(any(Query.class), any(Document.class), eq("idempotency_records")))
			.thenReturn(UpdateResult.acknowledged(0, 0L, null));
		RecordingPathMigrationService service = service(mongo);
		write(OLD_PATH, "reverse rollback".getBytes());

		assertThatThrownBy(service::migrate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("document conflict");

		ArgumentCaptor<Document> cleanupRollback = ArgumentCaptor.forClass(Document.class);
		ArgumentCaptor<Document> taskRollback = ArgumentCaptor.forClass(Document.class);
		ArgumentCaptor<Document> mediaRollback = ArgumentCaptor.forClass(Document.class);
		InOrder order = inOrder(mongo);
		order.verify(mongo).replace(argThat(this::usesIdAndVersion), any(Document.class), eq("media_assets"));
		order.verify(mongo).replace(argThat(this::usesIdAndVersion), any(Document.class), eq("task_items"));
		order.verify(mongo).replace(
			argThat(this::usesIdAndVersion), any(Document.class), eq("media_cleanup_jobs")
		);
		order.verify(mongo).replace(
			argThat(this::usesIdAndVersion), any(Document.class), eq("idempotency_records")
		);
		order.verify(mongo).replace(
			argThat(this::usesIdAndVersion), cleanupRollback.capture(), eq("media_cleanup_jobs")
		);
		order.verify(mongo).replace(
			argThat(this::usesIdAndVersion), taskRollback.capture(), eq("task_items")
		);
		order.verify(mongo).replace(
			argThat(this::usesIdAndVersion), mediaRollback.capture(), eq("media_assets")
		);
		assertThat(cleanupRollback.getValue().getList("relativePaths", String.class)).contains(OLD_PATH);
		assertThat(taskRollback.getValue().get("currentResult").toString()).contains(OLD_PATH);
		assertThat(mediaRollback.getValue().getString("relativePath")).isEqualTo(OLD_PATH);
		assertThat(storage().resolve(OLD_PATH)).exists();
		assertThat(storage().resolve(TARGET_PATH)).doesNotExist();
	}

	@Test
	void identicalTargetDatabaseFailureKeepsBothFileCopies() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		when(mongo.replace(any(Query.class), any(Document.class), eq("task_items")))
			.thenReturn(UpdateResult.acknowledged(0, 0L, null));
		RecordingPathMigrationService service = service(mongo);
		byte[] audio = "dedup rollback".getBytes();
		write(OLD_PATH, audio);
		write(TARGET_PATH, audio);

		assertThatThrownBy(service::migrate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("document conflict");

		assertThat(storage().resolve(OLD_PATH)).hasBinaryContent(audio);
		assertThat(storage().resolve(TARGET_PATH)).hasBinaryContent(audio);
		verify(mongo, times(2)).replace(any(Query.class), any(Document.class), eq("media_assets"));
	}

	@Test
	void onlySpecifiedFieldsAreReplacedAndOtherMatchingBusinessStringsStayUntouched() throws Exception {
		MongoTemplate mongo = mongoWithLegacyDocuments();
		RecordingPathMigrationService service = service(mongo);
		write(OLD_PATH, "targeted fields".getBytes());

		service.migrate();

		ArgumentCaptor<Document> media = ArgumentCaptor.forClass(Document.class);
		verify(mongo).replace(any(Query.class), media.capture(), eq("media_assets"));
		assertThat(media.getValue().getString("relativePath")).isEqualTo(TARGET_PATH);
		assertThat(media.getValue().getString("sourceErrorSummary")).isEqualTo(OLD_PATH);

		ArgumentCaptor<Document> task = ArgumentCaptor.forClass(Document.class);
		verify(mongo).replace(any(Query.class), task.capture(), eq("task_items"));
		assertThat(task.getValue().getString("referenceText")).isEqualTo(OLD_PATH);
		Document operation = task.getValue().getList("operations", Document.class).get(0);
		assertThat(operation.getString("description")).isEqualTo(OLD_PATH);

		ArgumentCaptor<Document> cleanup = ArgumentCaptor.forClass(Document.class);
		verify(mongo).replace(any(Query.class), cleanup.capture(), eq("media_cleanup_jobs"));
		assertThat(cleanup.getValue().getList("relativePaths", String.class)).contains(TARGET_PATH);
		assertThat(cleanup.getValue().getString("lastErrorSummary")).isEqualTo(OLD_PATH);

		ArgumentCaptor<Document> idempotency = ArgumentCaptor.forClass(Document.class);
		verify(mongo).replace(any(Query.class), idempotency.capture(), eq("idempotency_records"));
		assertThat(idempotency.getValue().getString("responseJson"))
			.contains("\"relativePath\":\"" + TARGET_PATH + "\"")
			.contains("prefix-" + OLD_PATH + "-suffix");
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

	private MongoTemplate mongoWithLegacyDocuments(Document... suppliedMedia) {
		MongoTemplate mongo = mock(MongoTemplate.class);
		List<Document> media = suppliedMedia.length == 0
			? List.of(legacyMedia("media-1"))
			: List.of(suppliedMedia);
		Document taskItem = versioned("item-1")
			.append("referenceText", OLD_PATH)
			.append("currentResult", new Document("audio", new Document("relativePath", OLD_PATH)))
			.append("operations", List.of(new Document("resultSnapshot",
				new Document("audio", new Document("relativePath", OLD_PATH)))
				.append("description", OLD_PATH)));
		Document cleanup = versioned("cleanup-1")
			.append("relativePaths", List.of(OLD_PATH, "temp/backups/keep.mp3"))
			.append("lastErrorSummary", OLD_PATH);
		Document idempotency = versioned("idem-1")
			.append("responseJson", "{\"audio\":{\"relativePath\":\"" + OLD_PATH
				+ "\"},\"note\":\"prefix-" + OLD_PATH + "-suffix\"}");

		when(mongo.find(any(Query.class), eq(Document.class), any(String.class)))
			.thenAnswer(invocation -> switch (invocation.getArgument(2, String.class)) {
				case "media_assets" -> media;
				case "task_items" -> List.of(taskItem);
				case "media_cleanup_jobs" -> List.of(cleanup);
				case "idempotency_records" -> List.of(idempotency);
				default -> List.of();
			});
		when(mongo.replace(any(Query.class), any(Document.class), any(String.class)))
			.thenReturn(UpdateResult.acknowledged(1, 1L, null));
		return mongo;
	}

	private Document legacyMedia(String id) {
		return versioned(id)
			.append("kind", "RECORDING")
			.append("relativePath", OLD_PATH)
			.append("sourceErrorSummary", OLD_PATH);
	}

	private Document versioned(String id) {
		return new Document("_id", id).append("version", 4L);
	}

	private boolean usesIdAndVersion(Query query) {
		return query != null
			&& query.getQueryObject().keySet().equals(java.util.Set.of("_id", "version"))
			&& query.getQueryObject().get("_id") != null
			&& query.getQueryObject().get("version").equals(4L);
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

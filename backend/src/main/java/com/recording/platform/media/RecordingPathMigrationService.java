package com.recording.platform.media;

import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class RecordingPathMigrationService {
	private static final Pattern LEGACY = Pattern.compile(
		"^recordings/([A-Za-z0-9_-]{1,128})/([A-Za-z0-9_-]{1,128})/current\\.(wav|mp3)$"
	);
	private static final int HASH_BUFFER_BYTES = 8192;
	private static final List<String> COLLECTIONS = List.of(
		"media_assets",
		"task_items",
		"media_cleanup_jobs",
		"idempotency_records"
	);

	private final RecordingMediaStorage storage;
	private final MongoTemplate mongo;

	public RecordingPathMigrationService(RecordingMediaStorage storage, MongoTemplate mongo) {
		this.storage = storage;
		this.mongo = mongo;
	}

	public RecordingPathMigrationResult migrate() {
		MigrationPlan plan = preflight();
		List<FilePlan> movedFiles = new ArrayList<>();
		List<DocumentChange> writtenDocuments = new ArrayList<>();
		try {
			prepareFiles(plan.files(), movedFiles);
			writeDocuments(plan.changes(), writtenDocuments);
			deleteDeduplicatedSources(plan.files());
			return new RecordingPathMigrationResult(
				(int) plan.files().stream().filter(file -> !file.deduplicate()).count(),
				(int) plan.files().stream().filter(FilePlan::deduplicate).count()
			);
		} catch (Exception failure) {
			boolean documentsRestored = rollbackDocuments(writtenDocuments, failure);
			recoverFiles(plan.files(), movedFiles, documentsRestored, failure);
			if (failure instanceof RuntimeException runtime) throw runtime;
			throw new IllegalStateException("recording path migration failed", failure);
		}
	}

	String targetPath(String oldPath) {
		Matcher matcher = LEGACY.matcher(oldPath == null ? "" : oldPath);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("unsupported legacy recording path");
		}
		return matcher.group(1) + "/" + matcher.group(2) + "." + matcher.group(3);
	}

	private MigrationPlan preflight() {
		Query legacyRecordings = Query.query(
			Criteria.where("kind").is("RECORDING").and("relativePath").regex(LEGACY)
		);
		List<Document> assets = mongo.find(legacyRecordings, Document.class, "media_assets");
		Map<String, List<Object>> mediaIdsByPath = new LinkedHashMap<>();
		for (Document asset : assets) {
			String oldPath = asset.getString("relativePath");
			targetPath(oldPath);
			mediaIdsByPath.computeIfAbsent(oldPath, ignored -> new ArrayList<>()).add(asset.get("_id"));
		}

		List<FilePlan> files = new ArrayList<>();
		for (Map.Entry<String, List<Object>> grouped : mediaIdsByPath.entrySet()) {
			files.add(inspect(grouped.getValue(), grouped.getKey()));
		}
		List<DocumentChange> changes = loadChanges(files);
		validateEntryAssets(files, changes);
		return new MigrationPlan(List.copyOf(files), changes);
	}

	private void validateEntryAssets(List<FilePlan> files, List<DocumentChange> changes) {
		for (FilePlan file : files) {
			for (Object mediaId : file.mediaIds()) {
				boolean planned = mediaId != null && changes.stream().anyMatch(change ->
					change.collection().equals("media_assets")
						&& change.id().equals(mediaId)
						&& change.mappings().contains(new PathMapping(file.oldPath(), file.newPath()))
				);
				if (!planned) {
					throw new IllegalStateException("migration media document changed during preflight");
				}
			}
		}
	}

	private FilePlan inspect(List<Object> mediaIds, String oldPath) {
		String newPath = targetPath(oldPath);
		Path source = storage.resolve(oldPath);
		if (!Files.isRegularFile(source)) {
			throw new IllegalStateException("legacy recording file is missing");
		}
		try {
			long sizeBytes = Files.size(source);
			String hash = sha256(source);
			Path target = storage.resolve(newPath);
			boolean deduplicate = Files.exists(target);
			if (deduplicate && (Files.size(target) != sizeBytes || !sha256(target).equals(hash))) {
				throw new IllegalStateException("recording path migration conflict");
			}
			return new FilePlan(
				List.copyOf(mediaIds), oldPath, newPath, sizeBytes, hash, deduplicate
			);
		} catch (IOException exception) {
			throw new IllegalStateException("legacy recording file cannot be inspected", exception);
		}
	}

	private List<DocumentChange> loadChanges(List<FilePlan> files) {
		Map<DocumentKey, MutableDocumentChange> changes = new LinkedHashMap<>();
		for (FilePlan file : files) {
			PathMapping mapping = new PathMapping(file.oldPath(), file.newPath());
			for (String collection : COLLECTIONS) {
				Query query = referenceQuery(collection, file.oldPath());
				for (Document found : mongo.find(query, Document.class, collection)) {
					mergeChange(changes, collection, found, mapping);
				}
			}
		}
		List<DocumentChange> result = new ArrayList<>();
		for (MutableDocumentChange change : changes.values()) {
			if (change.mappings.isEmpty()) continue;
			Document replacement = copyDocument(change.replacement);
			replacement.put("version", change.originalVersion + 1);
			Document rollback = copyDocument(change.original);
			rollback.put("version", change.originalVersion + 2);
			result.add(new DocumentChange(
				change.collection,
				change.id,
				change.originalVersion,
				copyDocument(change.original),
				replacement,
				rollback,
				List.copyOf(change.mappings)
			));
		}
		return List.copyOf(result);
	}

	private void mergeChange(
		Map<DocumentKey, MutableDocumentChange> changes,
		String collection,
		Document found,
		PathMapping mapping
	) {
		Document snapshot = copyDocument(found);
		Object id = snapshot.get("_id");
		Object versionValue = snapshot.get("version");
		if (id == null || !(versionValue instanceof Number version)) {
			throw new IllegalStateException("migration document has no id or version");
		}
		DocumentKey key = new DocumentKey(collection, id);
		MutableDocumentChange change = changes.get(key);
		if (change == null) {
			change = new MutableDocumentChange(
				collection,
				id,
				version.longValue(),
				copyDocument(snapshot),
				copyDocument(snapshot)
			);
			changes.put(key, change);
		} else if (change.originalVersion != version.longValue() || !change.original.equals(snapshot)) {
			throw new IllegalStateException("migration document changed during preflight");
		}
		if (replaceSpecifiedFields(collection, change.replacement, mapping.oldPath(), mapping.newPath())
			&& !change.mappings.contains(mapping)) {
			change.mappings.add(mapping);
		}
	}

	private void prepareFiles(List<FilePlan> files, List<FilePlan> movedFiles) throws IOException {
		for (FilePlan file : files) {
			if (file.deduplicate()) continue;
			move(storage.resolve(file.oldPath()), storage.resolve(file.newPath()));
			movedFiles.add(file);
		}
	}

	private void writeDocuments(List<DocumentChange> changes, List<DocumentChange> writtenDocuments) {
		for (DocumentChange change : changes) {
			UpdateResult result;
			try {
				result = mongo.replace(
					casQuery(change, false),
					copyDocument(change.replacement()),
					change.collection()
				);
			} catch (RuntimeException uncertainFailure) {
				writtenDocuments.add(change);
				throw new IllegalStateException("recording path migration document write failed", uncertainFailure);
			}
			if (!result.wasAcknowledged() || result.getMatchedCount() != 1) {
				throw new IllegalStateException("recording path migration document conflict");
			}
			writtenDocuments.add(change);
		}
	}

	private void deleteDeduplicatedSources(List<FilePlan> files) throws IOException {
		for (FilePlan file : files) {
			if (file.deduplicate()) Files.delete(storage.resolve(file.oldPath()));
		}
	}

	private boolean rollbackDocuments(List<DocumentChange> writtenDocuments, Exception failure) {
		boolean restoredAll = true;
		for (int index = writtenDocuments.size() - 1; index >= 0; index--) {
			DocumentChange change = writtenDocuments.get(index);
			try {
				UpdateResult restored = mongo.replace(
					casQuery(change, true),
					copyDocument(change.rollback()),
					change.collection()
				);
				if (!restored.wasAcknowledged() || restored.getMatchedCount() != 1) {
					restoredAll = false;
					failure.addSuppressed(new IllegalStateException("migration document rollback conflict"));
				}
			} catch (RuntimeException rollbackFailure) {
				restoredAll = false;
				failure.addSuppressed(rollbackFailure);
			}
		}
		return restoredAll;
	}

	private void recoverFiles(
		List<FilePlan> files,
		List<FilePlan> movedFiles,
		boolean documentsRestored,
		Exception failure
	) {
		for (int index = files.size() - 1; index >= 0; index--) {
			FilePlan file = files.get(index);
			Path source = storage.resolve(file.oldPath());
			Path target = storage.resolve(file.newPath());
			if (file.deduplicate()) {
				ensureOldCopy(source, target, failure);
				continue;
			}
			if (!movedFiles.contains(file) || !Files.exists(target) || Files.exists(source)) continue;
			if (documentsRestored) {
				try {
					move(target, source);
					continue;
				} catch (IOException moveFailure) {
					failure.addSuppressed(moveFailure);
				}
			}
			ensureOldCopy(source, target, failure);
		}
	}

	private void ensureOldCopy(Path source, Path target, Exception failure) {
		if (Files.exists(source) || !Files.exists(target)) return;
		try {
			Files.createDirectories(source.getParent());
			Files.copy(target, source);
		} catch (IOException copyFailure) {
			failure.addSuppressed(copyFailure);
		}
	}

	private Query casQuery(DocumentChange change, boolean rollback) {
		long version = rollback ? change.originalVersion() + 1 : change.originalVersion();
		List<Document> conditions = new ArrayList<>();
		conditions.add(new Document("_id", change.id()));
		conditions.add(new Document("version", version));
		for (PathMapping mapping : change.mappings()) {
			String expectedPath = rollback ? mapping.newPath() : mapping.oldPath();
			conditions.add(referenceQuery(change.collection(), expectedPath).getQueryObject());
		}
		return new BasicQuery(new Document("$and", conditions));
	}

	private Query referenceQuery(String collection, String path) {
		return switch (collection) {
			case "media_assets" -> Query.query(Criteria.where("relativePath").is(path));
			case "task_items" -> Query.query(new Criteria().orOperator(
				Criteria.where("currentResult.audio.relativePath").is(path),
				Criteria.where("operations.resultSnapshot.audio.relativePath").is(path)
			));
			case "media_cleanup_jobs" -> Query.query(Criteria.where("relativePaths").is(path));
			case "idempotency_records" -> Query.query(
				Criteria.where("responseJson").regex(
					Pattern.compile(Pattern.quote("\"" + path + "\""))
				)
			);
			default -> throw new IllegalArgumentException("unsupported migration collection");
		};
	}

	private void move(Path source, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			throw new IOException("recording storage does not support atomic migration", exception);
		}
	}

	private String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(path)) {
				byte[] buffer = new byte[HASH_BUFFER_BYTES];
				for (int read; (read = input.read(buffer)) >= 0;) {
					if (read > 0) digest.update(buffer, 0, read);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private boolean replaceResponseJson(Document document, String oldPath, String newPath) {
		String responseJson = document.getString("responseJson");
		String oldValue = "\"" + oldPath + "\"";
		if (responseJson == null || !responseJson.contains(oldValue)) return false;
		document.put("responseJson", responseJson.replace(oldValue, "\"" + newPath + "\""));
		return true;
	}

	private boolean replaceSpecifiedFields(
		String collection,
		Document document,
		String oldPath,
		String newPath
	) {
		return switch (collection) {
			case "media_assets" -> replaceMapValue(document, "relativePath", oldPath, newPath);
			case "task_items" -> replaceTaskItemPaths(document, oldPath, newPath);
			case "media_cleanup_jobs" -> replaceListValues(
				document.get("relativePaths"), oldPath, newPath
			);
			case "idempotency_records" -> replaceResponseJson(document, oldPath, newPath);
			default -> throw new IllegalArgumentException("unsupported migration collection");
		};
	}

	private boolean replaceTaskItemPaths(Document document, String oldPath, String newPath) {
		boolean changed = false;
		changed |= replaceResultAudioPath(document.get("currentResult"), oldPath, newPath);
		Object operations = document.get("operations");
		if (operations instanceof List<?> list) {
			for (Object operation : list) {
				if (operation instanceof Map<?, ?> operationMap) {
					changed |= replaceResultAudioPath(
						operationMap.get("resultSnapshot"), oldPath, newPath
					);
				}
			}
		}
		return changed;
	}

	private boolean replaceResultAudioPath(Object result, String oldPath, String newPath) {
		if (!(result instanceof Map<?, ?> resultMap)) return false;
		Object audio = resultMap.get("audio");
		if (!(audio instanceof Map<?, ?> audioMap)) return false;
		return replaceMapValue(audioMap, "relativePath", oldPath, newPath);
	}

	private boolean replaceListValues(Object value, String oldPath, String newPath) {
		boolean changed = false;
		if (value instanceof List<?> list) {
			@SuppressWarnings("unchecked")
			List<Object> mutable = (List<Object>) list;
			for (int index = 0; index < mutable.size(); index++) {
				if (oldPath.equals(mutable.get(index))) {
					mutable.set(index, newPath);
					changed = true;
				}
			}
		}
		return changed;
	}

	private boolean replaceMapValue(Map<?, ?> map, String key, String oldPath, String newPath) {
		if (!oldPath.equals(map.get(key))) return false;
		@SuppressWarnings("unchecked")
		Map<Object, Object> mutable = (Map<Object, Object>) map;
		mutable.put(key, newPath);
		return true;
	}

	private Document copyDocument(Document source) {
		Document copy = new Document();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			copy.put(entry.getKey(), copyValue(entry.getValue()));
		}
		return copy;
	}

	private Object copyValue(Object value) {
		if (value instanceof Document document) return copyDocument(document);
		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> copy = new LinkedHashMap<>();
			map.forEach((key, child) -> copy.put(key, copyValue(child)));
			return copy;
		}
		if (value instanceof List<?> list) {
			List<Object> copy = new ArrayList<>(list.size());
			for (Object child : list) copy.add(copyValue(child));
			return copy;
		}
		return value;
	}

	private record MigrationPlan(List<FilePlan> files, List<DocumentChange> changes) {
	}

	private record FilePlan(
		List<Object> mediaIds,
		String oldPath,
		String newPath,
		long sizeBytes,
		String sha256,
		boolean deduplicate
	) {
	}

	private record PathMapping(String oldPath, String newPath) {
	}

	private record DocumentKey(String collection, Object id) {
	}

	private static final class MutableDocumentChange {
		private final String collection;
		private final Object id;
		private final long originalVersion;
		private final Document original;
		private final Document replacement;
		private final List<PathMapping> mappings = new ArrayList<>();

		private MutableDocumentChange(
			String collection,
			Object id,
			long originalVersion,
			Document original,
			Document replacement
		) {
			this.collection = collection;
			this.id = id;
			this.originalVersion = originalVersion;
			this.original = original;
			this.replacement = replacement;
		}
	}

	private record DocumentChange(
		String collection,
		Object id,
		long originalVersion,
		Document original,
		Document replacement,
		Document rollback,
		List<PathMapping> mappings
	) {
	}
}

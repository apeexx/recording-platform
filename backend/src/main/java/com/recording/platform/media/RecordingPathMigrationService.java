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
		Query legacyRecordings = Query.query(
			Criteria.where("kind").is("RECORDING").and("relativePath").regex(LEGACY)
		);
		List<Document> assets = mongo.find(legacyRecordings, Document.class, "media_assets");
		int migrated = 0;
		int deduplicated = 0;
		for (Document asset : assets) {
			String oldPath = asset.getString("relativePath");
			MigrationEntry entry = inspect(asset.get("_id"), oldPath);
			boolean targetExists = Files.exists(storage.resolve(entry.newPath()));
			if (targetExists) {
				assertSameContent(entry);
			}
			List<DocumentChange> changes = loadChanges(entry.oldPath(), entry.newPath());
			apply(entry, changes, targetExists);
			if (targetExists) deduplicated++;
			else migrated++;
		}
		return new RecordingPathMigrationResult(migrated, deduplicated);
	}

	String targetPath(String oldPath) {
		Matcher matcher = LEGACY.matcher(oldPath == null ? "" : oldPath);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("unsupported legacy recording path");
		}
		return matcher.group(1) + "/" + matcher.group(2) + "." + matcher.group(3);
	}

	private MigrationEntry inspect(Object mediaId, String oldPath) {
		String newPath = targetPath(oldPath);
		Path source = storage.resolve(oldPath);
		if (!Files.isRegularFile(source)) {
			throw new IllegalStateException("legacy recording file is missing");
		}
		try {
			return new MigrationEntry(mediaId, oldPath, newPath, Files.size(source), sha256(source));
		} catch (IOException exception) {
			throw new IllegalStateException("legacy recording file cannot be inspected", exception);
		}
	}

	private void assertSameContent(MigrationEntry entry) {
		Path target = storage.resolve(entry.newPath());
		try {
			if (Files.size(target) != entry.sizeBytes() || !sha256(target).equals(entry.sha256())) {
				throw new IllegalStateException("recording path migration conflict");
			}
		} catch (IOException exception) {
			throw new IllegalStateException("recording path migration target cannot be inspected", exception);
		}
	}

	private List<DocumentChange> loadChanges(String oldPath, String newPath) {
		List<DocumentChange> changes = new ArrayList<>();
		for (String collection : COLLECTIONS) {
			for (Document found : mongo.find(referenceQuery(collection, oldPath), Document.class, collection)) {
				Document original = copyDocument(found);
				Object id = original.get("_id");
				Object version = original.get("version");
				if (id == null || version == null) {
					throw new IllegalStateException("migration document has no id or version");
				}
				Document replacement = copyDocument(original);
				boolean changed = collection.equals("idempotency_records")
					? replaceResponseJson(replacement, oldPath, newPath)
					: replaceExactStrings(replacement, oldPath, newPath);
				if (changed) {
					changes.add(new DocumentChange(collection, id, version, original, replacement));
				}
			}
		}
		return changes;
	}

	private Query referenceQuery(String collection, String oldPath) {
		return switch (collection) {
			case "media_assets" -> Query.query(Criteria.where("relativePath").is(oldPath));
			case "task_items" -> Query.query(new Criteria().orOperator(
				Criteria.where("currentResult.audio.relativePath").is(oldPath),
				Criteria.where("operations.resultSnapshot.audio.relativePath").is(oldPath)
			));
			case "media_cleanup_jobs" -> Query.query(Criteria.where("relativePaths").is(oldPath));
			case "idempotency_records" -> Query.query(
				Criteria.where("responseJson").regex(Pattern.compile(Pattern.quote(oldPath)))
			);
			default -> throw new IllegalArgumentException("unsupported migration collection");
		};
	}

	private void apply(MigrationEntry entry, List<DocumentChange> changes, boolean deduplicate) {
		Path source = storage.resolve(entry.oldPath());
		Path target = storage.resolve(entry.newPath());
		boolean moved = false;
		List<DocumentChange> written = new ArrayList<>();
		try {
			if (!deduplicate) {
				move(source, target);
				moved = true;
			}
			for (DocumentChange change : changes) {
				UpdateResult result = mongo.replace(
					versionQuery(change),
					copyDocument(change.replacement()),
					change.collection()
				);
				if (!result.wasAcknowledged() || result.getMatchedCount() != 1) {
					throw new IllegalStateException("recording path migration document conflict");
				}
				written.add(change);
			}
			if (deduplicate) {
				Files.delete(source);
			}
		} catch (Exception failure) {
			rollback(written, moved, source, target, failure);
			if (failure instanceof RuntimeException runtime) throw runtime;
			throw new IllegalStateException("recording path migration failed", failure);
		}
	}

	private void rollback(
		List<DocumentChange> written,
		boolean moved,
		Path source,
		Path target,
		Exception failure
	) {
		for (int index = written.size() - 1; index >= 0; index--) {
			DocumentChange change = written.get(index);
			try {
				UpdateResult restored = mongo.replace(
					versionQuery(change),
					copyDocument(change.original()),
					change.collection()
				);
				if (!restored.wasAcknowledged() || restored.getMatchedCount() != 1) {
					failure.addSuppressed(new IllegalStateException("migration document rollback conflict"));
				}
			} catch (RuntimeException rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
		}
		if (moved) {
			try {
				move(target, source);
			} catch (IOException rollbackFailure) {
				failure.addSuppressed(rollbackFailure);
			}
		}
	}

	private Query versionQuery(DocumentChange change) {
		return Query.query(
			Criteria.where("_id").is(change.id()).and("version").is(change.version())
		);
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

	private boolean replaceExactStrings(Object value, String oldPath, String newPath) {
		boolean changed = false;
		if (value instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				Object child = entry.getValue();
				if (oldPath.equals(child)) {
					@SuppressWarnings("unchecked")
					Map<Object, Object> mutable = (Map<Object, Object>) map;
					mutable.put(entry.getKey(), newPath);
					changed = true;
				} else {
					changed |= replaceExactStrings(child, oldPath, newPath);
				}
			}
		} else if (value instanceof List<?> list) {
			@SuppressWarnings("unchecked")
			List<Object> mutable = (List<Object>) list;
			for (int index = 0; index < mutable.size(); index++) {
				Object child = mutable.get(index);
				if (oldPath.equals(child)) {
					mutable.set(index, newPath);
					changed = true;
				} else {
					changed |= replaceExactStrings(child, oldPath, newPath);
				}
			}
		}
		return changed;
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

	private record MigrationEntry(Object mediaId, String oldPath, String newPath, long sizeBytes, String sha256) {
	}

	private record DocumentChange(
		String collection,
		Object id,
		Object version,
		Document original,
		Document replacement
	) {
	}
}

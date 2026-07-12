package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImportSourceStorage {
	private final Path root;

	@Autowired
	public ImportSourceStorage(@Value("${recording.storage-dir:backend/storage/recording-data}") String root) {
		this(Path.of(root));
	}

	public ImportSourceStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }

	public StoredImportSource save(String jobId, MultipartFile file) {
		String extension = extension(file.getOriginalFilename());
		if (!extension.equals("csv") && !extension.equals("xlsx")) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMPORT_FILE_TYPE_UNSUPPORTED", "导入文件只支持 .csv 或 .xlsx");
		}
		Path target = resolve("temp/imports/" + safe(jobId) + "/source." + extension);
		try {
			Files.createDirectories(target.getParent());
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = new java.security.DigestInputStream(file.getInputStream(), digest)) {
				Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return new StoredImportSource(relative(target), HexFormat.of().formatHex(digest.digest()), Files.size(target));
		} catch (IOException | NoSuchAlgorithmException exception) {
			delete(relative(target));
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_STORAGE_FAILED", "导入文件暂时无法保存");
		}
	}

	public Path resolve(String relative) {
		Path path = root.resolve(relative).normalize();
		if (!path.startsWith(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "PATH_TRAVERSAL_BLOCKED", "导入文件路径不合法");
		return path;
	}

	public String retainFailedRows(
		String jobId,
		String attemptId,
		List<ImportRow> rows,
		Set<Long> failedRows
	) {
		String retryFilename = "retry-" + safe(attemptId) + ".csv";
		Path target = resolve("temp/imports/" + safe(jobId) + "/" + retryFilename);
		Path temporary = target.resolveSibling(retryFilename + ".tmp");
		Map<Long, ImportRow> byNumber = new HashMap<>();
		long maximumRow = 1;
		for (ImportRow row : rows) {
			byNumber.put(row.rowNumber(), row);
			maximumRow = Math.max(maximumRow, row.rowNumber());
		}
		try {
			Files.createDirectories(target.getParent());
			try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);
				CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
				csv.printRecord(ImportFileParser.COLUMNS);
				for (long rowNumber = 2; rowNumber <= maximumRow; rowNumber++) {
					ImportRow row = byNumber.get(rowNumber);
					if (failedRows.contains(rowNumber) && row != null) {
						csv.printRecord(
							row.externalItemId(), row.referenceText(), row.referenceAudioUrl(), row.referenceVideoUrl()
						);
					} else {
						csv.printRecord("__redacted_row_" + rowNumber, "", "", "");
					}
				}
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return relative(target);
		} catch (IOException exception) {
			delete(relative(temporary));
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_STORAGE_FAILED", "失败行暂时无法安全保存");
		}
	}

	public void delete(String relative) {
		if (relative == null) return;
		try { Files.deleteIfExists(resolve(relative)); } catch (IOException ignored) { }
	}

	private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }
	private String safe(String value) {
		if (value == null || !value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("invalid job id");
		return value;
	}
	private String extension(String filename) {
		if (filename == null) return "";
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}

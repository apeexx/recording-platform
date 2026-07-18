package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImportFileParser {
	public static final int DEFAULT_MAX_ROWS = 50_000;
	public static final List<String> COLUMNS = List.of(
		"externalItemId", "referenceText", "referenceAudioUrl", "referenceVideoUrl"
	);
	private final int maxRows;

	public ImportFileParser() { this(DEFAULT_MAX_ROWS); }

	ImportFileParser(int maxRows) {
		if (maxRows < 1) throw new IllegalArgumentException("maxRows must be positive");
		this.maxRows = maxRows;
	}

	public List<ImportRow> parse(Path path, String originalFilename) {
		String extension = extension(originalFilename);
		try {
			if (!"csv".equals(extension)) {
				throw new ApiException(
					HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"IMPORT_FILE_TYPE_UNSUPPORTED",
					"导入文件只支持 .csv"
				);
			}
			return parseCsv(path);
		} catch (ApiException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IMPORT_FILE_INVALID", "导入文件无法解析");
		}
	}

	private List<ImportRow> parseCsv(Path path) throws IOException {
		CSVFormat format = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setIgnoreEmptyLines(true)
			.get();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(withoutBom(Files.newInputStream(path)), StandardCharsets.UTF_8));
			CSVParser parser = format.parse(reader)) {
			validateHeaders(parser.getHeaderNames());
			List<ImportRow> rows = new ArrayList<>();
			for (CSVRecord record : parser) {
				if (rows.size() >= maxRows) throw rowLimitExceeded();
				rows.add(new ImportRow(
					record.getRecordNumber() + 1,
					blankToNull(record.get(COLUMNS.get(0))),
					blankToNull(record.get(COLUMNS.get(1))),
					blankToNull(record.get(COLUMNS.get(2))),
					blankToNull(record.get(COLUMNS.get(3)))
				));
			}
			return rows;
		}
	}

	private InputStream withoutBom(InputStream raw) throws IOException {
		PushbackInputStream input = new PushbackInputStream(raw, 3);
		byte[] prefix = input.readNBytes(3);
		if (!(prefix.length == 3 && (prefix[0] & 0xff) == 0xef && (prefix[1] & 0xff) == 0xbb
			&& (prefix[2] & 0xff) == 0xbf)) {
			input.unread(prefix);
		}
		return input;
	}

	private void validateHeaders(List<String> headers) {
		if (!COLUMNS.equals(headers)) throw headerInvalid();
	}

	private ApiException headerInvalid() {
		return new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"IMPORT_HEADER_INVALID",
			"导入表头必须依次为 externalItemId、referenceText、referenceAudioUrl、referenceVideoUrl"
		);
	}

	private ApiException rowLimitExceeded() {
		return new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"IMPORT_ROW_LIMIT_EXCEEDED",
			"单次导入最多支持 " + maxRows + " 行"
		);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String extension(String filename) {
		if (filename == null) return "";
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}

package com.recording.platform.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportFileParserTests {
	@TempDir
	Path tempDir;

	@Test
	void csvUsesTheFixedColumnsAndXlsxIsRejected() throws Exception {
		ImportFileParser parser = new ImportFileParser();
		Path csv = tempDir.resolve("items.csv");
		Files.writeString(csv,
			"externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\n"
				+ "ext-1,请朗读,https://cdn.example.com/a.wav,\n"
		);
		List<ImportRow> csvRows = parser.parse(csv, "items.csv");
		assertThat(csvRows).singleElement().satisfies((row) -> {
			assertThat(row.rowNumber()).isEqualTo(2);
			assertThat(row.externalItemId()).isEqualTo("ext-1");
			assertThat(row.referenceText()).isEqualTo("请朗读");
			assertThat(row.referenceAudioUrl()).isEqualTo("https://cdn.example.com/a.wav");
		});

		Path xlsx = tempDir.resolve("items.xlsx");
		Files.write(xlsx, new byte[] {1, 2, 3});
		assertThatThrownBy(() -> parser.parse(xlsx, "items.xlsx"))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE);
				assertThat(exception.getCode()).isEqualTo("IMPORT_FILE_TYPE_UNSUPPORTED");
			});
	}

	@Test
	void missingOrRenamedHeadersAreRejected() throws Exception {
		Path csv = tempDir.resolve("bad.csv");
		Files.writeString(csv, "id,text,audio,video\n1,hello,,\n");

		assertThatThrownBy(() -> new ImportFileParser().parse(csv, "bad.csv"))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("IMPORT_HEADER_INVALID")
			);
	}

	@Test
	void configuredRowLimitBoundsParserMemory() throws Exception {
		Path csv = tempDir.resolve("too-many.csv");
		Files.writeString(
			csv,
			"externalItemId,referenceText,referenceAudioUrl,referenceVideoUrl\n"
				+ "row-1,第一条,,\nrow-2,第二条,,\nrow-3,第三条,,\n"
		);

		assertThatThrownBy(() -> new ImportFileParser(2).parse(csv, "too-many.csv"))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("IMPORT_ROW_LIMIT_EXCEEDED")
			);
	}
}

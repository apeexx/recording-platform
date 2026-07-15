package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import com.recording.platform.security.PlatformPrincipal;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/media")
public class MediaController {
	private final MediaAccessService access;

	public MediaController(MediaAccessService access) {
		this.access = access;
	}

	@GetMapping("/{mediaId}")
	public ResponseEntity<StreamingResponseBody> read(
		@PathVariable String mediaId,
		@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		ReadableMedia readable = access.open(mediaId, actor);
		HttpHeaders headers = baseHeaders(readable.contentType());
		if (rangeHeader == null || rangeHeader.isBlank()) {
			headers.setContentLength(readable.length());
			StreamingResponseBody body = output -> streamRange(readable, 0, readable.length(), output);
			return new ResponseEntity<>(body, headers, HttpStatus.OK);
		}
		try {
			List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
			if (ranges.size() != 1) throw invalidRange();
			HttpRange range = ranges.get(0);
			long start = range.getRangeStart(readable.length());
			long end = range.getRangeEnd(readable.length());
			long count = end - start + 1;
			if (start >= readable.length() || count < 1) throw invalidRange();
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + readable.length());
			headers.setContentLength(count);
			StreamingResponseBody body = output -> streamRange(readable, start, count, output);
			return new ResponseEntity<>(body, headers, HttpStatus.PARTIAL_CONTENT);
		} catch (IllegalArgumentException exception) {
			throw invalidRange();
		}
	}

	private void streamRange(ReadableMedia readable, long start, long count, OutputStream output) throws IOException {
		try (InputStream input = Files.newInputStream(readable.path())) {
			input.skipNBytes(start);
			byte[] buffer = new byte[8192];
			long remaining = count;
			while (remaining > 0) {
				int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
				if (read < 0) throw new EOFException("media file changed while streaming");
				output.write(buffer, 0, read);
				remaining -= read;
			}
		}
	}

	private HttpHeaders baseHeaders(String contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
		headers.setCacheControl(CacheControl.noStore());
		try {
			headers.setContentType(MediaType.parseMediaType(contentType));
		} catch (IllegalArgumentException exception) {
			headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		}
		return headers;
	}

	private ApiException invalidRange() {
		return new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "INVALID_RANGE", "Range 请求不合法");
	}
}

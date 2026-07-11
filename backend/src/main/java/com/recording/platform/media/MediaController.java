package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import com.recording.platform.security.PlatformPrincipal;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
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

@RestController
@RequestMapping("/api/media")
public class MediaController {
	private final MediaAccessService access;

	public MediaController(MediaAccessService access) {
		this.access = access;
	}

	@GetMapping("/{mediaId}")
	public ResponseEntity<?> read(
		@PathVariable String mediaId,
		@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		ReadableMedia readable = access.open(mediaId, actor);
		Resource resource = new FileSystemResource(readable.path());
		HttpHeaders headers = baseHeaders(readable.contentType());
		if (rangeHeader == null || rangeHeader.isBlank()) {
			headers.setContentLength(readable.length());
			return new ResponseEntity<>(resource, headers, HttpStatus.OK);
		}
		try {
			List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
			if (ranges.size() != 1) throw invalidRange();
			ResourceRegion region = ranges.get(0).toResourceRegion(resource);
			if (region.getPosition() >= readable.length() || region.getCount() < 1) throw invalidRange();
			long end = region.getPosition() + region.getCount() - 1;
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + region.getPosition() + "-" + end + "/" + readable.length());
			headers.setContentLength(region.getCount());
			return new ResponseEntity<>(region, headers, HttpStatus.PARTIAL_CONTENT);
		} catch (IllegalArgumentException exception) {
			throw invalidRange();
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

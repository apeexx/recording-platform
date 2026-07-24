package com.recording.platform.integration;

import com.recording.platform.media.MediaController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/integrations/items")
public class IntegrationResultController {
	private final IntegrationResultService results;

	public IntegrationResultController(IntegrationResultService results) {
		this.results = results;
	}

	@GetMapping("/{itemId}")
	public IntegrationResultView get(@PathVariable String itemId) {
		return results.get(itemId);
	}

	@GetMapping("/{itemId}/audio")
	public ResponseEntity<StreamingResponseBody> readAudio(
		@PathVariable String itemId,
		@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
	) {
		return MediaController.response(results.openAudio(itemId), rangeHeader);
	}
}

package com.recording.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
	String code,
	String message,
	String requestId,
	Map<String, Object> details
) {
}

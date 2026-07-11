package com.recording.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorWriter {
	private final ObjectMapper objectMapper;

	public ApiErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(
		HttpServletRequest request,
		HttpServletResponse response,
		HttpStatus status,
		String code,
		String message
	) throws IOException {
		write(request, response, status, code, message, null);
	}

	public void write(
		HttpServletRequest request,
		HttpServletResponse response,
		HttpStatus status,
		String code,
		String message,
		Map<String, Object> details
	) throws IOException {
		String requestId = RequestIdFilter.currentRequestId(request);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
		response.setHeader(RequestIdFilter.HEADER_NAME, requestId);
		objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(code, message, requestId, details));
	}
}

package com.recording.platform.voice;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class VoiceGenerationErrorHandler {
	@ExceptionHandler(VoiceGenerationException.class)
	ResponseEntity<Map<String, Object>> handleVoiceGenerationException(VoiceGenerationException exception) {
		return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> handleValidationException() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "请求参数不完整或不合法"));
	}
}

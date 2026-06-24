package com.recording.platform.voice;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException() {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(Map.of("error", "上传音频文件过大，请使用不超过 20MB 的 mp3、m4a 或 wav 母带音频"));
	}
}

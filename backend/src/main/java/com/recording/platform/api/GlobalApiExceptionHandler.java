package com.recording.platform.api;

import com.recording.platform.voice.VoiceGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
		return response(exception.getStatus(), exception.getCode(), exception.getMessage(), exception.getDetails(), request);
	}

	@ExceptionHandler(VoiceGenerationException.class)
	ResponseEntity<ApiErrorResponse> handleVoiceGenerationException(
		VoiceGenerationException exception,
		HttpServletRequest request
	) {
		return response(HttpStatus.BAD_REQUEST, "VOICE_GENERATION_ERROR", exception.getMessage(), null, request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidationException(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不完整或不合法", null, request);
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MissingServletRequestParameterException.class,
		MissingServletRequestPartException.class,
		MethodArgumentTypeMismatchException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式或参数不合法", null, request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
		return response(
			HttpStatus.UNSUPPORTED_MEDIA_TYPE,
			"UNSUPPORTED_MEDIA_TYPE",
			"请求 Content-Type 不受支持",
			null,
			request
		);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceededException(HttpServletRequest request) {
		return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "上传内容超过 100MB 全局限制", null, request);
	}

	@ExceptionHandler(DuplicateKeyException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicateKey(HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "数据已存在或状态已变化", null, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求资源不存在", null, request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiErrorResponse> handleAccessDenied(HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作", null, request);
	}

	@ExceptionHandler(DataAccessResourceFailureException.class)
	ResponseEntity<ApiErrorResponse> handleDatabaseUnavailable(HttpServletRequest request) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库暂时不可用", null, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpectedException(HttpServletRequest request) {
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试", null, request);
	}

	private ResponseEntity<ApiErrorResponse> response(
		HttpStatus status,
		String code,
		String message,
		Map<String, Object> details,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
			code,
			message,
			RequestIdFilter.currentRequestId(request),
			details
		));
	}
}

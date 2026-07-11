package com.recording.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalApiExceptionHandlerTests {

	@ParameterizedTest
	@MethodSource("mappedStatuses")
	void mapsBusinessStatusesToTheUnifiedErrorContract(int statusCode) throws Exception {
		MockMvc mockMvc = mockMvc();

		mockMvc.perform(get("/errors/{statusCode}", statusCode).header(RequestIdFilter.HEADER_NAME, "request-123"))
			.andExpect(status().is(statusCode))
			.andExpect(header().string(RequestIdFilter.HEADER_NAME, "request-123"))
			.andExpect(jsonPath("$.code").value("ERROR_" + statusCode))
			.andExpect(jsonPath("$.message").value("safe message"))
			.andExpect(jsonPath("$.requestId").value("request-123"))
			.andExpect(jsonPath("$.details.status").value(statusCode));
	}

	@Test
	void unexpectedExceptionsReturnASanitized500WithoutLeakingTheOriginalMessage() throws Exception {
		mockMvc().perform(get("/errors/runtime"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("database-password")
			)))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void globalMultipartOverflowUsesTheUnified413Contract() throws Exception {
		mockMvc().perform(get("/errors/upload"))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void invalidIncomingRequestIdIsReplacedWithANonEmptyServerGeneratedValue() throws Exception {
		mockMvc().perform(get("/errors/400").header(RequestIdFilter.HEADER_NAME, "!!!"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string(RequestIdFilter.HEADER_NAME, org.hamcrest.Matchers.not("")))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void malformedJsonAndMissingRequestParametersAlsoUseTheUnified400Contract() throws Exception {
		MockMvc mockMvc = mockMvc();
		mockMvc.perform(post("/errors/validate").contentType("application/json").content("{bad-json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
		mockMvc.perform(get("/errors/required"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void unsupportedContentTypeUsesTheUnified415ContractInsteadOfA500() throws Exception {
		mockMvc().perform(post("/errors/validate").contentType("text/plain").content("{}"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void missingMultipartAudioUsesTheUnified400ContractInsteadOfA500() throws Exception {
		mockMvc().perform(multipart("/errors/multipart"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.standaloneSetup(new FailureController())
			.setControllerAdvice(new GlobalApiExceptionHandler())
			.addFilters(new RequestIdFilter())
			.build();
	}

	private static Stream<Integer> mappedStatuses() {
		return Stream.of(400, 401, 403, 404, 409, 413, 422, 429, 503);
	}

	@RestController
	private static class FailureController {
		@GetMapping("/errors/{statusCode}")
		void business(@PathVariable int statusCode) {
			throw new ApiException(
				HttpStatus.valueOf(statusCode),
				"ERROR_" + statusCode,
				"safe message",
				Map.of("status", statusCode)
			);
		}

		@GetMapping("/errors/runtime")
		void runtime() {
			throw new IllegalStateException("database-password=should-never-leak");
		}

		@GetMapping("/errors/upload")
		void upload() {
			throw new MaxUploadSizeExceededException(100L * 1024 * 1024);
		}

		@PostMapping("/errors/validate")
		void validate(@Valid @RequestBody RequiredBody body) {
		}

		@GetMapping("/errors/required")
		void required(@RequestParam String value) {
		}

		@PostMapping("/errors/multipart")
		void multipart(@RequestPart MultipartFile audio) {
		}
	}

	private record RequiredBody(@NotBlank String value) {
	}
}

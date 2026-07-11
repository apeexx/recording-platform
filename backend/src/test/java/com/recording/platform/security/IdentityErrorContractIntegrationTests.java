package com.recording.platform.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.RecordingPlatformBackendApplication;
import com.recording.platform.api.ApiException;
import com.recording.platform.api.RequestIdFilter;
import com.recording.platform.identity.dto.CreateBackendUserRequest;
import com.recording.platform.identity.service.AdminUserService;
import com.recording.platform.identity.service.SessionService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
	classes = {RecordingPlatformBackendApplication.class, IdentityErrorContractIntegrationTests.TestEndpoint.class},
	properties = {
		"spring.data.mongodb.auto-index-creation=false",
		"INITIAL_ADMIN_USERNAME=",
		"INITIAL_ADMIN_PASSWORD="
	}
)
@AutoConfigureMockMvc
class IdentityErrorContractIntegrationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AdminUserService adminUserService;

	@MockitoBean
	private SessionService sessionService;

	@ParameterizedTest
	@MethodSource("weakInitialPasswords")
	void adminCreationRoutesWeakInitialPasswordValuesToTheDocumented422Contract(
		String initialPassword
	) throws Exception {
		doThrow(new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"PASSWORD_TOO_WEAK",
			"初始密码至少需要 8 个字符，且 UTF-8 编码不能超过 72 字节"
		)).when(adminUserService).create(any(CreateBackendUserRequest.class));

		mockMvc.perform(post("/api/admin/users")
				.with(user("admin").roles("ADMIN"))
				.with(csrf())
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(Map.of(
					"username", "reviewer-1",
					"name", "一审员",
					"role", "REVIEWER",
					"initialPassword", initialPassword
				))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void databaseFailureDuringSessionAuthenticationUsesTheUnified503Contract() throws Exception {
		when(sessionService.authenticateWeb("database-failure-token")).thenThrow(
			new DataAccessResourceFailureException("mongodb-password=should-never-leak")
		);

		mockMvc.perform(get("/api/reviewer/error-contract-probe")
				.header(RequestIdFilter.HEADER_NAME, "request-database-failure")
				.cookie(new Cookie(SessionAuthenticationFilter.WEB_COOKIE_NAME, "database-failure-token")))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().string(RequestIdFilter.HEADER_NAME, "request-database-failure"))
			.andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
			.andExpect(jsonPath("$.message").value("数据库暂时不可用"))
			.andExpect(jsonPath("$.requestId").value("request-database-failure"))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("mongodb-password")
			)));
	}

	@Test
	void unexpectedFailureDuringSessionAuthenticationUsesTheUnified500Contract() throws Exception {
		when(sessionService.authenticateWeb("unexpected-failure-token")).thenThrow(
			new IllegalStateException("session-secret=should-never-leak")
		);

		mockMvc.perform(get("/api/reviewer/error-contract-probe")
				.header(RequestIdFilter.HEADER_NAME, "request-unexpected-failure")
				.cookie(new Cookie(SessionAuthenticationFilter.WEB_COOKIE_NAME, "unexpected-failure-token")))
			.andExpect(status().isInternalServerError())
			.andExpect(header().string(RequestIdFilter.HEADER_NAME, "request-unexpected-failure"))
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试"))
			.andExpect(jsonPath("$.requestId").value("request-unexpected-failure"))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("session-secret")
			)));
	}

	private static Stream<String> weakInitialPasswords() {
		return Stream.of("", "short", "x".repeat(129));
	}

	@RestController
	static class TestEndpoint {
		@GetMapping("/api/reviewer/error-contract-probe")
		String probe() {
			return "ok";
		}
	}
}

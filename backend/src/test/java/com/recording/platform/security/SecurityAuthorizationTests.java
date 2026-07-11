package com.recording.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.RecordingPlatformBackendApplication;
import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.SessionIdentity;
import com.recording.platform.identity.service.SessionService;
import com.recording.platform.identity.service.WeChatAuthenticationService;
import com.recording.platform.identity.service.WebAuthenticationService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
	classes = {RecordingPlatformBackendApplication.class, SecurityAuthorizationTests.TestEndpoints.class},
	properties = {
		"spring.data.mongodb.auto-index-creation=false",
		"INITIAL_ADMIN_USERNAME=",
		"INITIAL_ADMIN_PASSWORD="
	}
)
@AutoConfigureMockMvc
class SecurityAuthorizationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired(required = false)
	private List<FilterRegistrationBean<?>> filterRegistrations = List.of();

	@Autowired
	private ApplicationContext applicationContext;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private WebAuthenticationService webAuthenticationService;

	@MockitoBean
	private WeChatAuthenticationService weChatAuthenticationService;

	@Test
	void onlyAdminCanAccessUserManagementAndVoiceGeneration() throws Exception {
		mockMvc.perform(get("/api/admin/probe").with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/probe").with(user("reviewer").roles("REVIEWER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/probe").with(user("collector").roles("COLLECTOR")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/voice-generation/probe").with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/voice-generation/probe").with(user("reviewer").roles("REVIEWER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void authenticatedReviewerCanAccessNonAdminApiButAnonymousCannot() throws Exception {
		mockMvc.perform(get("/api/reviewer/probe").with(user("reviewer").roles("REVIEWER")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/reviewer/probe"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
			.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void authenticationFiltersAreNotRegisteredOutsideTheSpringSecurityChain() {
		assertDisabledServletRegistration(SessionAuthenticationFilter.class);
		assertDisabledServletRegistration(FirstPasswordChangeFilter.class);
	}

	@Test
	void customOpaqueSessionAuthenticationDoesNotCreateOrPrintASpringGeneratedPassword() {
		org.assertj.core.api.Assertions.assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void miniprogramLoginRejectsClientSuppliedOpenIdInsteadOfIgnoringIt() throws Exception {
		mockMvc.perform(post("/api/auth/miniprogram/login")
				.contentType("application/json")
				.content("{\"code\":\"temporary-code\",\"openId\":\"forged-open-id\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void authenticatedWebRequestsRenewTheIdleCookieLifetime() throws Exception {
		when(sessionService.authenticateWeb("raw-web-token")).thenReturn(new SessionIdentity(
			"session-1",
			"admin-1",
			"admin",
			"管理员",
			UserRole.ADMIN,
			SessionType.WEB,
			false
		));

		MvcResult result = mockMvc.perform(get("/api/reviewer/probe")
				.cookie(new Cookie(SessionAuthenticationFilter.WEB_COOKIE_NAME, "raw-web-token")))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anySatisfy((header) -> assertThat(header)
				.contains("REC_WEB_SESSION=raw-web-token")
				.contains("Max-Age=43200")
				.contains("HttpOnly")
				.contains("SameSite=Lax")
				.contains("Path=/"));
	}

	@Test
	void unsafeAdminRequestsRequireTheReadableCookieCsrfToken() throws Exception {
		mockMvc.perform(post("/api/admin/probe").with(user("admin").roles("ADMIN")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		MvcResult tokenResponse = mockMvc.perform(get("/api/auth/web/csrf")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
			.andReturn();
		Cookie csrfCookie = tokenResponse.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrfCookie).isNotNull();
		assertThat(csrfCookie.isHttpOnly()).isFalse();

		mockMvc.perform(post("/api/admin/probe")
				.with(user("admin").roles("ADMIN"))
				.cookie(csrfCookie)
				.header("X-XSRF-TOKEN", csrfCookie.getValue()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/admin/probe")
				.with(user("admin").roles("ADMIN"))
				.with(csrf()))
			.andExpect(status().isOk());
	}

	@Test
	void firstPasswordChangeSessionCanFetchTheCsrfTokenRequiredForPasswordUpdate() throws Exception {
		PlatformPrincipal principal = new PlatformPrincipal(
			"session-1",
			"admin-1",
			"admin",
			"管理员",
			UserRole.ADMIN,
			SessionType.WEB,
			true
		);

		mockMvc.perform(get("/api/auth/web/csrf")
				.with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
	}

	@Test
	void weakPasswordValueUsesTheDocumented422Contract() throws Exception {
		PlatformPrincipal principal = new PlatformPrincipal(
			"session-1",
			"admin-1",
			"admin",
			"管理员",
			UserRole.ADMIN,
			SessionType.WEB,
			false
		);
		doThrow(new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"PASSWORD_TOO_WEAK",
			"新密码至少需要 8 个字符"
		)).when(webAuthenticationService).changePassword("admin-1", "CurrentPassword-1", "short");

		mockMvc.perform(put("/api/auth/web/password")
				.with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_ADMIN")))
				.with(csrf())
				.contentType("application/json")
				.content("{\"currentPassword\":\"CurrentPassword-1\",\"newPassword\":\"short\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
	}

	@Test
	void oversizedPasswordValueAlsoUsesTheDocumented422Contract() throws Exception {
		PlatformPrincipal principal = new PlatformPrincipal(
			"session-1",
			"admin-1",
			"admin",
			"管理员",
			UserRole.ADMIN,
			SessionType.WEB,
			false
		);
		String oversizedPassword = "x".repeat(129);
		doThrow(new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"PASSWORD_TOO_WEAK",
			"新密码长度必须为 8 到 128 个字符"
		)).when(webAuthenticationService).changePassword(
			"admin-1",
			"CurrentPassword-1",
			oversizedPassword
		);

		mockMvc.perform(put("/api/auth/web/password")
				.with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_ADMIN")))
				.with(csrf())
				.contentType("application/json")
				.content("{\"currentPassword\":\"CurrentPassword-1\",\"newPassword\":\""
					+ oversizedPassword + "\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("PASSWORD_TOO_WEAK"));
	}

	@Test
	void invalidCollectorNameValueUsesTheDocumented422Contract() throws Exception {
		PlatformPrincipal principal = new PlatformPrincipal(
			"session-2",
			"collector-1",
			null,
			null,
			UserRole.COLLECTOR,
			SessionType.MINIPROGRAM,
			false
		);
		doThrow(new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"INVALID_NAME",
			"姓名不能为空且不能超过 64 个字符"
		)).when(weChatAuthenticationService).setName("collector-1", "   ");

		mockMvc.perform(put("/api/auth/miniprogram/name")
				.with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_COLLECTOR")))
				.contentType("application/json")
				.content("{\"name\":\"   \"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("INVALID_NAME"));
	}

	private void assertDisabledServletRegistration(Class<?> filterType) {
		org.assertj.core.api.Assertions.assertThat(filterRegistrations)
			.filteredOn((registration) -> filterType.isInstance(registration.getFilter()))
			.singleElement()
			.satisfies((registration) ->
				org.assertj.core.api.Assertions.assertThat(registration.isEnabled()).isFalse()
			);
	}

	@RestController
	static class TestEndpoints {
		@GetMapping({"/api/admin/probe", "/api/voice-generation/probe", "/api/reviewer/probe"})
		String probe() {
			return "ok";
		}

		@PostMapping("/api/admin/probe")
		String writeProbe() {
			return "ok";
		}
	}
}

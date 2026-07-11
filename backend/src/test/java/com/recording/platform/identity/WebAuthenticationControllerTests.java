package com.recording.platform.identity;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.identity.controller.WebAuthenticationController;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.service.WebAuthenticationService;
import com.recording.platform.identity.service.WebLoginResult;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebAuthenticationControllerTests {

	@Test
	void loginStoresOpaqueTokenOnlyInHttpOnlySameSiteLaxCookie() throws Exception {
		WebAuthenticationService authentication = org.mockito.Mockito.mock(WebAuthenticationService.class);
		when(authentication.login("admin", "InitialPassword-1")).thenReturn(new WebLoginResult(
			"raw-web-token",
			"session-1",
			"admin-1",
			"admin",
			"管理员",
			UserRole.ADMIN,
			true
		));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
			new WebAuthenticationController(authentication, false, 12)
		).build();

		mockMvc.perform(post("/api/auth/web/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"InitialPassword-1\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
				Matchers.containsString("REC_WEB_SESSION=raw-web-token"),
				Matchers.containsString("HttpOnly"),
				Matchers.containsString("SameSite=Lax"),
				Matchers.containsString("Path=/")
			)))
			.andExpect(jsonPath("$.token").doesNotExist())
			.andExpect(jsonPath("$.firstPasswordChangeRequired").value(true));
	}
}

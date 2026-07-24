package com.recording.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.api.ApiErrorWriter;
import com.recording.platform.api.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IntegrationApiKeyAuthenticationFilterTests {
	@Test
	void unconfiguredIntegrationReturnsARequestIdWithoutEchoingThePresentedKey() throws Exception {
		IntegrationApiKeyAuthenticationFilter filter = new IntegrationApiKeyAuthenticationFilter(
			new IntegrationApiKeyAuthenticator(""),
			new ApiErrorWriter(new ObjectMapper())
		);
		MockHttpServletRequest request = new MockHttpServletRequest(
			"POST",
			"/api/integrations/tasks/task-1/items"
		);
		request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "request-integration-unconfigured");
		request.addHeader(IntegrationApiKeyAuthenticationFilter.HEADER_NAME, "presented-secret-key");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getHeader(RequestIdFilter.HEADER_NAME))
			.isEqualTo("request-integration-unconfigured");
		assertThat(response.getContentAsString())
			.contains("\"code\":\"INTEGRATION_NOT_CONFIGURED\"")
			.doesNotContain("presented-secret-key");
	}
}

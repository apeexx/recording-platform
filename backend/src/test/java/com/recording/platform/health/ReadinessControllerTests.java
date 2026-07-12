package com.recording.platform.health;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReadinessControllerTests {
	@Test
	void returns200WhenReadyAnd503WhenUnavailable() throws Exception {
		ReadinessService service = mock(ReadinessService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReadinessController(service)).build();
		when(service.check()).thenReturn(new ReadinessService.Readiness("UP", "UP", "UP"));
		mvc.perform(get("/api/health/ready"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.overall").value("UP"));

		when(service.check()).thenReturn(new ReadinessService.Readiness("DOWN", "DOWN", "UP"));
		mvc.perform(get("/api/health/ready"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.mongo").value("DOWN"))
			.andExpect(jsonPath("$.storage").value("UP"))
			.andExpect(jsonPath("$.details").doesNotExist());
	}
}

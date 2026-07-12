package com.recording.platform.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.RecordingPlatformBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = RecordingPlatformBackendApplication.class,
	properties = {
		"spring.data.mongodb.auto-index-creation=false",
		"INITIAL_ADMIN_USERNAME=",
		"INITIAL_ADMIN_PASSWORD="
	}
)
@AutoConfigureMockMvc
class ReadinessSecurityIntegrationTests {
	@Autowired MockMvc mvc;
	@MockitoBean ReadinessService readiness;

	@Test
	void readinessIsPublicButNoOtherApiBoundaryIsRelaxed() throws Exception {
		when(readiness.check()).thenReturn(new ReadinessService.Readiness("UP", "UP", "UP"));
		mvc.perform(get("/api/health/ready"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overall").value("UP"));
		mvc.perform(get("/api/tasks"))
			.andExpect(status().isUnauthorized());
	}
}

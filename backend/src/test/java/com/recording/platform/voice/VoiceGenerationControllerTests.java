package com.recording.platform.voice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.voice.dto.VoiceGenerationResponse;
import com.recording.platform.voice.model.GenerationMode;
import com.recording.platform.voice.model.GenerationStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VoiceGenerationControllerTests {

	@Test
	void synthesizeReturnsGenerationResponse() throws Exception {
		VoiceGenerationService service = org.mockito.Mockito.mock(VoiceGenerationService.class);
		when(service.synthesize(any())).thenReturn(new VoiceGenerationResponse(
			"record-1",
			GenerationMode.SYNTHESIZE,
			GenerationStatus.COMPLETED,
			"生成成功",
			"/api/voice-generation/audio/record-1"
		));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VoiceGenerationController(service)).build();

		mockMvc.perform(post("/api/voice-generation/synthesize")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"voiceId":"voice-1","text":"测试文本","speed":1.0,"volume":1.0,"pitch":0}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recordId").value("record-1"))
			.andExpect(jsonPath("$.audioUrl").value("/api/voice-generation/audio/record-1"));
	}

	@Test
	void databaseUnavailableReturnsBadRequestJson() throws Exception {
		VoiceGenerationService service = org.mockito.Mockito.mock(VoiceGenerationService.class);
		doThrow(new DataAccessResourceFailureException("MongoDB unavailable"))
			.when(service)
			.deleteVoice("voice-1");
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VoiceGenerationController(service))
			.setControllerAdvice(new VoiceGenerationErrorHandler())
			.build();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
				"/api/voice-generation/voices/voice-1"
			))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("MongoDB 未连接，无法保存或读取语音生成数据"));
	}

	@Test
	void audioEndpointStreamsGeneratedAudio() throws Exception {
		VoiceGenerationService service = org.mockito.Mockito.mock(VoiceGenerationService.class);
		when(service.loadAudio("record-1")).thenReturn(new VoiceGenerationAudio(
			new ByteArrayResource("audio".getBytes(StandardCharsets.UTF_8)),
			"record-1.mp3",
			"audio/mpeg"
		));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VoiceGenerationController(service)).build();

		mockMvc.perform(get("/api/voice-generation/audio/record-1"))
			.andExpect(status().isOk())
			.andExpect(content().contentType("audio/mpeg"))
			.andExpect(content().bytes("audio".getBytes(StandardCharsets.UTF_8)));
	}
}

package com.recording.platform.voice;

import com.recording.platform.voice.dto.VoiceGenerationConfigRequest;
import com.recording.platform.voice.model.VoiceGenerationConfig;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice-generation/config")
public class VoiceGenerationConfigController {
	private final VoiceGenerationConfigService service;

	public VoiceGenerationConfigController(VoiceGenerationConfigService service) {
		this.service = service;
	}

	@GetMapping("/default")
	public VoiceGenerationConfig defaultConfig() {
		return service.defaultConfig();
	}

	@PutMapping("/default")
	public VoiceGenerationConfig saveDefaultConfig(@Valid @RequestBody VoiceGenerationConfigRequest request) {
		return service.saveDefaultConfig(request);
	}
}

package com.recording.platform.voice;

import com.recording.platform.voice.dto.VoiceGenerationConfigRequest;
import com.recording.platform.voice.model.VoiceGenerationConfig;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voice-generation/config")
public class VoiceGenerationConfigController {
	private static final String DEFAULT_CONFIG_ID = "default";
	private final AtomicReference<VoiceGenerationConfig> defaultConfig = new AtomicReference<>();
	private final Clock clock;

	public VoiceGenerationConfigController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping("/default")
	public VoiceGenerationConfig defaultConfig() {
		VoiceGenerationConfig config = defaultConfig.get();
		return config == null ? fallbackConfig() : config;
	}

	@PutMapping("/default")
	public VoiceGenerationConfig saveDefaultConfig(@Valid @RequestBody VoiceGenerationConfigRequest request) {
		VoiceGenerationConfig config = new VoiceGenerationConfig();
		config.setId(DEFAULT_CONFIG_ID);
		config.setVoiceId(request.voiceId());
		config.setSpeed(request.speed());
		config.setVolume(request.volume());
		config.setPitch(request.pitch());
		config.setUpdatedAt(Instant.now(clock));
		defaultConfig.set(config);
		return config;
	}

	private VoiceGenerationConfig fallbackConfig() {
		VoiceGenerationConfig config = new VoiceGenerationConfig();
		config.setId(DEFAULT_CONFIG_ID);
		config.setVoiceId("sichuan_native_01");
		config.setSpeed(0.9);
		config.setVolume(1.0);
		config.setPitch(0);
		return config;
	}
}

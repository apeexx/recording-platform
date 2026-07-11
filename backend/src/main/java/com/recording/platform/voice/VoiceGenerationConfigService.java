package com.recording.platform.voice;

import com.recording.platform.voice.dto.VoiceGenerationConfigRequest;
import com.recording.platform.voice.model.VoiceGenerationConfig;
import com.recording.platform.voice.repository.SpringDataVoiceGenerationConfigRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class VoiceGenerationConfigService {
	private static final String DEFAULT_CONFIG_ID = "default";
	private final SpringDataVoiceGenerationConfigRepository repository;
	private final Clock clock;

	public VoiceGenerationConfigService(SpringDataVoiceGenerationConfigRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public VoiceGenerationConfig defaultConfig() {
		return repository.findById(DEFAULT_CONFIG_ID).orElseGet(this::fallbackConfig);
	}

	public VoiceGenerationConfig saveDefaultConfig(VoiceGenerationConfigRequest request) {
		VoiceGenerationConfig config = new VoiceGenerationConfig();
		config.setId(DEFAULT_CONFIG_ID);
		config.setVoiceId(request.voiceId());
		config.setSpeed(request.speed());
		config.setVolume(request.volume());
		config.setPitch(request.pitch());
		config.setUpdatedAt(Instant.now(clock));
		return repository.save(config);
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

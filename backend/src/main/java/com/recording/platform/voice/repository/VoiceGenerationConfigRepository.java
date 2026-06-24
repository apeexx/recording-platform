package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceGenerationConfigRepository extends JpaRepository<VoiceGenerationConfig, String> {
}

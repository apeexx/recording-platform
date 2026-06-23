package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VoiceGenerationConfigRepository extends MongoRepository<VoiceGenerationConfig, String> {
}

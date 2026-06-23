package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VoiceGenerationRecordMongoRepository extends MongoRepository<VoiceGenerationRecord, String> {
	Page<VoiceGenerationRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

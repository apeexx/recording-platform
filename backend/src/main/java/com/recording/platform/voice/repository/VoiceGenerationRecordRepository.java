package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceGenerationRecordRepository extends JpaRepository<VoiceGenerationRecord, String> {
	Page<VoiceGenerationRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

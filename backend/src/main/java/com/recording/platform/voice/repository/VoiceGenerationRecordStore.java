package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public interface VoiceGenerationRecordStore {
	VoiceGenerationRecord save(VoiceGenerationRecord record);

	Optional<VoiceGenerationRecord> findById(String id);

	default Page<VoiceGenerationRecord> findRecent(Pageable pageable) {
		return new PageImpl<>(java.util.List.of(), pageable, 0);
	}
}

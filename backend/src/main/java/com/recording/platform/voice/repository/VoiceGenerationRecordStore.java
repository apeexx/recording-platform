package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import java.util.List;
import java.util.Optional;

public interface VoiceGenerationRecordStore {
	VoiceGenerationRecord save(VoiceGenerationRecord record);

	Optional<VoiceGenerationRecord> findById(String id);

	default List<VoiceGenerationRecord> findRecent(int page, int size) {
		return List.of();
	}

	default long count() {
		return 0;
	}
}

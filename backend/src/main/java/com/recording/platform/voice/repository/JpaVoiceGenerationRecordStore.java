package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaVoiceGenerationRecordStore implements VoiceGenerationRecordStore {
	private final VoiceGenerationRecordRepository repository;

	public JpaVoiceGenerationRecordStore(VoiceGenerationRecordRepository repository) {
		this.repository = repository;
	}

	@Override
	public VoiceGenerationRecord save(VoiceGenerationRecord record) {
		return repository.save(record);
	}

	@Override
	public Optional<VoiceGenerationRecord> findById(String id) {
		return repository.findById(id);
	}

	@Override
	public Page<VoiceGenerationRecord> findRecent(Pageable pageable) {
		return repository.findAllByOrderByCreatedAtDesc(pageable);
	}
}

package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class MongoVoiceGenerationRecordStore implements VoiceGenerationRecordStore {
	private final SpringDataVoiceGenerationRecordRepository repository;

	public MongoVoiceGenerationRecordStore(SpringDataVoiceGenerationRecordRepository repository) {
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
	public List<VoiceGenerationRecord> findRecent(int page, int size) {
		return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)).getContent();
	}

	@Override
	public long count() {
		return repository.count();
	}
}

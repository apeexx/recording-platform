package com.recording.platform.voice.repository;

import com.recording.platform.voice.model.VoiceGenerationRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryVoiceGenerationRecordStore implements VoiceGenerationRecordStore {
	private final Map<String, VoiceGenerationRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized VoiceGenerationRecord save(VoiceGenerationRecord record) {
		if (record.getId() == null || record.getId().isBlank()) {
			record.setId(UUID.randomUUID().toString());
		}
		records.put(record.getId(), record);
		return record;
	}

	@Override
	public synchronized Optional<VoiceGenerationRecord> findById(String id) {
		return Optional.ofNullable(records.get(id));
	}

	@Override
	public synchronized List<VoiceGenerationRecord> findRecent(int page, int size) {
		List<VoiceGenerationRecord> sorted = new ArrayList<>(records.values());
		sorted.sort(Comparator
			.comparing(VoiceGenerationRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
			.reversed());
		int start = Math.min(Math.max(page, 0) * Math.max(size, 1), sorted.size());
		int end = Math.min(start + Math.max(size, 1), sorted.size());
		return List.copyOf(sorted.subList(start, end));
	}

	@Override
	public synchronized long count() {
		return records.size();
	}
}

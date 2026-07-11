package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.store.PlatformStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class MongoPlatformStore implements PlatformStore {
	private final SpringDataPlatformRepository repository;

	public MongoPlatformStore(SpringDataPlatformRepository repository) {
		this.repository = repository;
	}

	@Override public PlatformRecord save(PlatformRecord platform) { return repository.save(platform); }
	@Override public Optional<PlatformRecord> findById(String id) { return repository.findById(id); }
	@Override public Optional<PlatformRecord> findByCode(String code) { return repository.findByCode(code); }
	@Override public Page<PlatformRecord> findAll(Pageable pageable) { return repository.findAll(pageable); }
	@Override public void deleteById(String id) { repository.deleteById(id); }
}

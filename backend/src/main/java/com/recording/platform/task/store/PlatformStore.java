package com.recording.platform.task.store;

import com.recording.platform.task.model.PlatformRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformStore {
	PlatformRecord save(PlatformRecord platform);
	Optional<PlatformRecord> findById(String id);
	Optional<PlatformRecord> findByCode(String code);
	Page<PlatformRecord> findAll(Pageable pageable);
	void deleteById(String id);
}

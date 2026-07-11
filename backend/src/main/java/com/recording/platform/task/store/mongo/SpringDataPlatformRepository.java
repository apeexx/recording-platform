package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.PlatformRecord;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataPlatformRepository extends MongoRepository<PlatformRecord, String> {
	Optional<PlatformRecord> findByCode(String code);
}

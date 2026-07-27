package com.recording.platform.batch.store.mongo;

import com.recording.platform.batch.model.BatchOperationSnapshot;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataBatchOperationSnapshotRepository extends MongoRepository<BatchOperationSnapshot, String> {
	List<BatchOperationSnapshot> findAllByJobIdOrderBySequenceAsc(String jobId);
}

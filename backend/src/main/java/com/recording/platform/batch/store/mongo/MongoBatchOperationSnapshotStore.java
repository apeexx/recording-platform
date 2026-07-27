package com.recording.platform.batch.store.mongo;

import com.recording.platform.batch.model.BatchOperationSnapshot;
import com.recording.platform.batch.store.BatchOperationSnapshotStore;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MongoBatchOperationSnapshotStore implements BatchOperationSnapshotStore {
	private final SpringDataBatchOperationSnapshotRepository repository;
	MongoBatchOperationSnapshotStore(SpringDataBatchOperationSnapshotRepository repository) {
		this.repository = repository;
	}
	@Override public void saveAll(List<BatchOperationSnapshot> snapshots) { repository.saveAll(snapshots); }
	@Override public List<BatchOperationSnapshot> findAllByJobId(String jobId) {
		return repository.findAllByJobIdOrderBySequenceAsc(jobId);
	}
}

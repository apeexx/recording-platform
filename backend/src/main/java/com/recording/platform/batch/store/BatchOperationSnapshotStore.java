package com.recording.platform.batch.store;

import com.recording.platform.batch.model.BatchOperationSnapshot;
import java.util.List;

public interface BatchOperationSnapshotStore {
	void saveAll(List<BatchOperationSnapshot> snapshots);
	default List<BatchOperationSnapshot> findAllByJobId(String jobId) { return List.of(); }
}

package com.recording.platform.operation.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OperationQueryStore {
	Page<OperationEntry> findOperations(String actorUserId, Pageable pageable);
}

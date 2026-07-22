package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskItem;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Service;

@Service
public class TaskPendingIndexMigrationService {
	static final String LEGACY_GLOBAL_INDEX = "unique_collector_recording_pending";
	static final String TASK_SCOPED_INDEX = "unique_collector_task_recording_pending";
	static final String QUERY_INDEX = "collector_task_status";

	private final MongoTemplate mongo;

	public TaskPendingIndexMigrationService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public boolean migrate() {
		IndexOperations indexes = mongo.indexOps(TaskItem.class);
		Index queryIndex = new Index()
			.on("collectorId", Sort.Direction.ASC)
			.on("taskId", Sort.Direction.ASC)
			.on("status", Sort.Direction.ASC)
			.named(QUERY_INDEX);
		indexes.createIndex(queryIndex);
		var names = indexes.getIndexInfo().stream().map(index -> index.getName()).toList();
		boolean legacyExists = names.contains(LEGACY_GLOBAL_INDEX);
		boolean taskScopedExists = names.contains(TASK_SCOPED_INDEX);
		if (legacyExists) indexes.dropIndex(LEGACY_GLOBAL_INDEX);
		if (taskScopedExists) indexes.dropIndex(TASK_SCOPED_INDEX);
		return legacyExists || taskScopedExists;
	}
}

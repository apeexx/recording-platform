package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class TaskPendingIndexMigrationService {
	static final String LEGACY_GLOBAL_INDEX = "unique_collector_recording_pending";
	static final String TASK_SCOPED_INDEX = "unique_collector_task_recording_pending";

	private final MongoTemplate mongo;

	public TaskPendingIndexMigrationService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public boolean migrate() {
		IndexOperations indexes = mongo.indexOps(TaskItem.class);
		Index taskScoped = new Index()
			.on("collectorId", Sort.Direction.ASC)
			.on("taskId", Sort.Direction.ASC)
			.unique()
			.partial(PartialIndexFilter.of(
				Criteria.where("status").is(TaskItemStatus.RECORDING_PENDING)
			))
			.named(TASK_SCOPED_INDEX);
		indexes.createIndex(taskScoped);
		boolean legacyExists = indexes.getIndexInfo().stream()
			.anyMatch(index -> LEGACY_GLOBAL_INDEX.equals(index.getName()));
		if (legacyExists) indexes.dropIndex(LEGACY_GLOBAL_INDEX);
		return legacyExists;
	}
}

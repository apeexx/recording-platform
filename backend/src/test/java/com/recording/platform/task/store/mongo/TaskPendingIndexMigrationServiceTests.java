package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.TaskItem;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

class TaskPendingIndexMigrationServiceTests {
	@Test
	void runnerSkipsOnlyTheDestructiveLocalResetMode() {
		ConditionalOnProperty condition = TaskPendingIndexMigrationRunner.class
			.getAnnotation(ConditionalOnProperty.class);

		assertThat(condition).isNotNull();
		assertThat(condition.name()).containsExactly("recording.local-reset.enabled");
		assertThat(condition.havingValue()).isEqualTo("false");
		assertThat(condition.matchIfMissing()).isTrue();
	}

	@Test
	void ensuresTheTaskScopedIndexBeforeDroppingTheLegacyGlobalIndex() {
		MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
		IndexOperations indexes = org.mockito.Mockito.mock(IndexOperations.class);
		IndexInfo legacy = org.mockito.Mockito.mock(IndexInfo.class);
		when(mongo.indexOps(TaskItem.class)).thenReturn(indexes);
		when(indexes.createIndex(any(IndexDefinition.class)))
			.thenReturn(TaskPendingIndexMigrationService.TASK_SCOPED_INDEX);
		when(legacy.getName()).thenReturn(TaskPendingIndexMigrationService.LEGACY_GLOBAL_INDEX);
		when(indexes.getIndexInfo()).thenReturn(List.of(legacy));

		boolean changed = new TaskPendingIndexMigrationService(mongo).migrate();

		InOrder ordered = inOrder(indexes);
		ordered.verify(indexes).createIndex(any(IndexDefinition.class));
		ordered.verify(indexes).getIndexInfo();
		ordered.verify(indexes).dropIndex(TaskPendingIndexMigrationService.LEGACY_GLOBAL_INDEX);
		assertThat(changed).isTrue();
		ArgumentCaptor<IndexDefinition> definition = ArgumentCaptor.forClass(IndexDefinition.class);
		verify(indexes).createIndex(definition.capture());
		assertThat(definition.getValue().getIndexKeys())
			.isEqualTo(new Document("collectorId", 1).append("taskId", 1));
		assertThat(definition.getValue().getIndexOptions())
			.containsEntry("name", TaskPendingIndexMigrationService.TASK_SCOPED_INDEX)
			.containsEntry("unique", true);
		assertThat(definition.getValue().getIndexOptions().get("partialFilterExpression").toString())
			.contains("status", "RECORDING_PENDING");
	}

	@Test
	void repeatedMigrationKeepsTheNewIndexAndDoesNotDropAnything() {
		MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
		IndexOperations indexes = org.mockito.Mockito.mock(IndexOperations.class);
		IndexInfo current = org.mockito.Mockito.mock(IndexInfo.class);
		when(mongo.indexOps(TaskItem.class)).thenReturn(indexes);
		when(indexes.createIndex(any(IndexDefinition.class)))
			.thenReturn(TaskPendingIndexMigrationService.TASK_SCOPED_INDEX);
		when(current.getName()).thenReturn(TaskPendingIndexMigrationService.TASK_SCOPED_INDEX);
		when(indexes.getIndexInfo()).thenReturn(List.of(current));

		boolean changed = new TaskPendingIndexMigrationService(mongo).migrate();

		assertThat(changed).isFalse();
		verify(indexes, never()).dropIndex(any(String.class));
	}

	@Test
	void failedNewIndexCreationLeavesTheLegacyIndexUntouched() {
		MongoTemplate mongo = org.mockito.Mockito.mock(MongoTemplate.class);
		IndexOperations indexes = org.mockito.Mockito.mock(IndexOperations.class);
		when(mongo.indexOps(TaskItem.class)).thenReturn(indexes);
		when(indexes.createIndex(any(IndexDefinition.class))).thenThrow(new IllegalStateException("index failed"));

		TaskPendingIndexMigrationService service = new TaskPendingIndexMigrationService(mongo);

		assertThatThrownBy(service::migrate).isInstanceOf(IllegalStateException.class);
		verify(indexes, never()).getIndexInfo();
		verify(indexes, never()).dropIndex(any(String.class));
	}
}

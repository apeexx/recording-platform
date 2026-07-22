package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.recording.platform.media.MediaAsset;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.model.TaskVersion;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

class TaskDomainMappingTests {
	@Test
	void collectionsExposeTheRequiredUniqueBusinessKeys() throws Exception {
		assertCollection(TaskRecord.class, "tasks");
		assertUniqueField(TaskRecord.class, "taskCode", false);
		assertCollection(TaskVersion.class, "task_versions");
		assertCompoundIndex(TaskVersion.class, true, "taskId", "versionNumber");
		assertCollection(TaskGrant.class, "task_grants");
		assertCompoundIndex(TaskGrant.class, true, "taskId", "userId");
		assertCollection(ImportJob.class, "import_jobs");
		assertCompoundIndex(ImportJob.class, true, "taskId", "operationId");
		assertCollection(MediaAsset.class, "media_assets");
		Indexed relativePath = MediaAsset.class.getDeclaredField("relativePath").getAnnotation(Indexed.class);
		assertThat(relativePath).isNotNull();
		assertThat(relativePath.unique())
			.as("同一任务条目重新提交会复用 current 文件路径，媒体元数据不能把该路径设为唯一")
			.isFalse();
	}

	@Test
	void tasksUseGeneratedCodesWithoutPlatformAndExposeResultType() {
		assertThat(Arrays.stream(TaskRecord.class.getDeclaredFields()).map(Field::getName))
			.doesNotContain("platformId");
		assertThat(Arrays.stream(TaskVersion.class.getDeclaredFields()).map(Field::getName))
			.contains("resultType");
		assertThatCode(() -> Class.forName("com.recording.platform.task.model.SequenceRecord"))
			.doesNotThrowAnyException();
	}

	@Test
	void accessRequestsAndItemsUsePartialUniqueIndexesForPendingWork() {
		assertCollection(TaskAccessRequest.class, "task_access_requests");
		CompoundIndex pendingRequest = findCompoundIndex(TaskAccessRequest.class, "taskId", "userId");
		assertThat(pendingRequest.unique()).isTrue();
		assertThat(pendingRequest.partialFilter()).contains("PENDING");

		assertCollection(TaskItem.class, "task_items");
		assertCompoundIndex(TaskItem.class, true, "taskId", "itemCode");
		assertThat(Arrays.stream(TaskItem.class.getDeclaredFields()).map(Field::getName))
			.doesNotContain("externalItemId");
		CompoundIndex collectorPending = findCompoundIndex(TaskItem.class, "collectorId", "taskId");
		assertThat(collectorPending.unique()).isTrue();
		assertThat(collectorPending.name()).isEqualTo("unique_collector_task_recording_pending");
		assertThat(collectorPending.partialFilter()).contains("RECORDING_PENDING");
		CompoundIndex creationOperation = findCompoundIndex(TaskItem.class, "taskId", "creationOperationId");
		assertThat(creationOperation.unique()).isTrue();
		assertThat(creationOperation.partialFilter()).contains("creationOperationId").contains("$type");
	}

	private void assertCollection(Class<?> type, String collection) {
		assertThat(type.getAnnotation(Document.class)).isNotNull();
		assertThat(type.getAnnotation(Document.class).collection()).isEqualTo(collection);
	}

	private void assertUniqueField(Class<?> type, String fieldName, boolean sparse) throws Exception {
		Field field = type.getDeclaredField(fieldName);
		Indexed indexed = field.getAnnotation(Indexed.class);
		assertThat(indexed).isNotNull();
		assertThat(indexed.unique()).isTrue();
		assertThat(indexed.sparse()).isEqualTo(sparse);
	}

	private void assertCompoundIndex(Class<?> type, boolean unique, String... fields) {
		assertThat(findCompoundIndex(type, fields).unique()).isEqualTo(unique);
	}

	private CompoundIndex findCompoundIndex(Class<?> type, String... fields) {
		CompoundIndexes indexes = type.getAnnotation(CompoundIndexes.class);
		assertThat(indexes).as(type.getSimpleName() + " compound indexes").isNotNull();
		return Arrays.stream(indexes.value())
			.filter((index) -> Arrays.stream(fields).allMatch((field) -> index.def().contains(field)))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing compound index " + Arrays.toString(fields)));
	}
}

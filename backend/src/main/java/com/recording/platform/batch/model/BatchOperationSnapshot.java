package com.recording.platform.batch.model;

import com.recording.platform.task.model.TaskItemStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "batch_operation_items")
@CompoundIndex(name = "unique_batch_item_sequence", def = "{'jobId':1,'sequence':1}", unique = true)
public class BatchOperationSnapshot {
	@Id
	private String id;
	private String jobId;
	private long sequence;
	private String itemId;
	private long expectedRevision;
	private TaskItemStatus status;
	private String collectorId;
	private String currentText;
}

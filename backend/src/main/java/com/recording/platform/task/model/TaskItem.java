package com.recording.platform.task.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "task_items")
@CompoundIndexes({
	@CompoundIndex(name = "unique_task_item_code", def = "{'taskId': 1, 'itemCode': 1}", unique = true),
	@CompoundIndex(
		name = "collector_task_status",
		def = "{'collectorId': 1, 'taskId': 1, 'status': 1}"
	),
	@CompoundIndex(name = "task_item_claim", def = "{'taskId': 1, 'status': 1, 'sequence': 1}"),
	@CompoundIndex(name = "collector_item_status", def = "{'collectorId': 1, 'status': 1}"),
	@CompoundIndex(
		name = "unique_task_item_creation_operation",
		def = "{'taskId': 1, 'creationOperationId': 1}",
		unique = true,
		partialFilter = "{'creationOperationId': {'$type': 'string'}}"
	)
})
public class TaskItem {
	@Id
	private String id;
	@Version
	private Long version;
	private String taskId;
	private long sequence;
	private String itemCode;
	private String creationOperationId;
	private TaskItemStatus status;
	private String collectorId;
	private String reviewerId;
	private String reviewAssignmentId;
	private String assignmentId;
	private long revision;
	private String referenceText;
	private String referenceAudioUrl;
	private String referenceVideoUrl;
	private String referenceAudioMediaId;
	private String referenceVideoMediaId;
	private TaskItemResult currentResult;
	private CurrentRejection currentRejection;
	private List<SubmissionHistory> submissions = new ArrayList<>();
	private List<OperationHistory> operations = new ArrayList<>();
	private TaskItemStatus discardedPreviousStatus;
	private Instant createdAt;
	private Instant updatedAt;
}

package com.recording.platform.task.model;

import com.recording.platform.task.store.ClaimMutation;
import com.recording.platform.task.store.RejectMutation;
import com.recording.platform.task.store.ReleaseMutation;
import com.recording.platform.task.store.SubmitMutation;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OperationHistory {
	private String operationId;
	private String type;
	private String actorUserId;
	private String actorUsername;
	private String content;
	private Instant occurredAt;
	private TaskItemStatus resultStatus;
	private long resultRevision;
	private String resultAssignmentId;
	private TaskItemResult resultSnapshot;

	public static OperationHistory claim(ClaimMutation mutation, TaskItem item) {
		return create(
			"claim:" + mutation.assignmentId(),
			"CLAIM",
			mutation.collectorId(),
			mutation.actorUsername(),
			mutation.actorUsername() + "领取了任务条目",
			mutation.occurredAt(),
			item
		);
	}

	public static OperationHistory submission(SubmitMutation mutation, TaskItem item) {
		return create(
			mutation.operationId(),
			"SUBMIT",
			mutation.collectorId(),
			mutation.actorUsername(),
			mutation.actorUsername() + "提交了录音结果",
			mutation.occurredAt(),
			item
		);
	}

	public static OperationHistory rejection(RejectMutation mutation, TaskItem item) {
		return create(
			mutation.operationId(),
			"REJECT",
			mutation.actorUserId(),
			mutation.actorUsername(),
			mutation.actorUsername() + "驳回了录音：" + mutation.reason(),
			mutation.occurredAt(),
			item
		);
	}

	public static OperationHistory release(ReleaseMutation mutation, TaskItem item) {
		return create(
			mutation.operationId(),
			"RELEASE",
			mutation.actorUserId(),
			mutation.actorUsername(),
			mutation.actorUsername() + "释放了任务条目",
			mutation.occurredAt(),
			item
		);
	}

	public static OperationHistory creation(
		String operationId,
		String actorUserId,
		String actorUsername,
		Instant occurredAt,
		TaskItem item
	) {
		return create(
			operationId,
			"ADD",
			actorUserId,
			actorUsername,
			actorUsername + "新增了任务条目",
			occurredAt,
			item
		);
	}

	public static OperationHistory referenceEdit(
		String operationId,
		String actorUserId,
		String actorUsername,
		Instant occurredAt,
		TaskItem item
	) {
		return create(
			operationId, "EDIT_REFERENCE", actorUserId, actorUsername,
			actorUsername + "修改了任务条目参考内容", occurredAt, item
		);
	}

	private static OperationHistory create(
		String operationId,
		String type,
		String actorUserId,
		String actorUsername,
		String content,
		Instant occurredAt,
		TaskItem item
	) {
		OperationHistory operation = new OperationHistory();
		operation.setOperationId(operationId);
		operation.setType(type);
		operation.setActorUserId(actorUserId);
		operation.setActorUsername(actorUsername);
		operation.setContent(content);
		operation.setOccurredAt(occurredAt);
		operation.setResultStatus(item.getStatus());
		operation.setResultRevision(item.getRevision());
		operation.setResultAssignmentId(item.getAssignmentId());
		operation.setResultSnapshot(item.getCurrentResult());
		return operation;
	}
}

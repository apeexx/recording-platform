package com.recording.platform.task.service;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class TaskItemStatusMigrationService {
	private final MongoTemplate mongo;

	public TaskItemStatusMigrationService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	public long migrateLegacyUnassignedReviews() {
		Criteria reviewerMissing = new Criteria().orOperator(
			Criteria.where("reviewerId").exists(false), Criteria.where("reviewerId").is(null)
		);
		Criteria assignmentMissing = new Criteria().orOperator(
			Criteria.where("reviewAssignmentId").exists(false), Criteria.where("reviewAssignmentId").is(null)
		);
		Query query = Query.query(new Criteria().andOperator(
			Criteria.where("status").is(TaskItemStatus.REVIEW_PENDING), reviewerMissing, assignmentMissing
		));
		return mongo.updateMulti(query, Update.update("status", TaskItemStatus.SUBMITTED), TaskItem.class)
			.getModifiedCount();
	}
}

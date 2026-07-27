package com.recording.platform.report.store;

import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.ReviewerSummary;
import com.recording.platform.report.dto.CollectorReportTask;
import com.recording.platform.report.dto.CollectorTaskDaySummary;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import com.recording.platform.report.dto.CollectorTaskReportSummary;
import com.recording.platform.task.model.TaskItemStatus;
import com.mongodb.client.MongoCollection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ArrayDeque;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoReportQueryStore implements ReportQueryStore {
	private final MongoCollection<Document> items;
	private final MongoCollection<Document> tasks;

	public MongoReportQueryStore(MongoTemplate mongoTemplate) {
		this.items = mongoTemplate.getCollection("task_items");
		this.tasks = mongoTemplate.getCollection("tasks");
	}

	@Override
	public WorkSummary aggregateWork(String collectorId, String taskId) {
		List<Document> pipeline = new ArrayList<>();
		Document match = new Document();
		if (collectorId != null) match.append("$or", List.of(
			new Document("collectorId", collectorId),
			new Document("submissions.collectorId", collectorId),
			new Document("operations.actorUserId", collectorId)
		));
		if (taskId != null) match.append("taskId", taskId);
		if (!match.isEmpty()) pipeline.add(new Document("$match", match));
		Document allSubmissions = new Document("$ifNull", List.of("$submissions", List.of()));
		Document submissions = collectorId == null ? allSubmissions
			: new Document("$filter", new Document("input", allSubmissions).append("as", "submission")
				.append("cond", new Document("$or", List.of(
					new Document("$eq", List.of("$$submission.collectorId", collectorId)),
					new Document("$and", List.of(
						new Document("$eq", java.util.Arrays.asList(
							new Document("$ifNull", java.util.Arrays.asList("$$submission.collectorId", null)), null
						)),
						new Document("$eq", List.of("$collectorId", collectorId))
					))
				))));
		Document operations = new Document("$ifNull", List.of("$operations", List.of()));
		pipeline.add(new Document("$project", new Document()
			.append("submissionCount", new Document("$size", submissions))
			.append("submissionDuration", new Document("$reduce", new Document()
				.append("input", submissions).append("initialValue", 0)
				.append("in", new Document("$add", List.of("$$value", new Document("$ifNull", List.of("$$this.durationMillis", 0)))))))
			.append("completed", new Document("$cond", List.of(completedCondition(collectorId), 1, 0)))
			.append("currentDuration", new Document("$cond", List.of(
				completedCondition(collectorId),
				new Document("$ifNull", List.of("$currentResult.audio.durationMillis", 0)), 0)))
			.append("releaseCount", countType(operations, "RELEASE"))
			.append("discardCount", countType(operations, "ADMIN_DISCARD"))));
		pipeline.add(new Document("$group", new Document("_id", null)
			.append("submissionCount", new Document("$sum", "$submissionCount"))
			.append("submissionDuration", new Document("$sum", "$submissionDuration"))
			.append("completed", new Document("$sum", "$completed"))
			.append("currentDuration", new Document("$sum", "$currentDuration"))
			.append("releaseCount", new Document("$sum", "$releaseCount"))
			.append("discardCount", new Document("$sum", "$discardCount"))));
		Document result = items.aggregate(pipeline).first();
		if (result == null) return new WorkSummary(0, 0, 0, 0, 0, 0);
		return new WorkSummary(number(result, "submissionCount"), number(result, "submissionDuration"),
			number(result, "completed"), number(result, "currentDuration"),
			number(result, "releaseCount"), number(result, "discardCount"));
	}

	@Override
	public Page<SubmissionView> findSubmissions(String collectorId, Pageable pageable) {
		List<Document> base = List.of(
			new Document("$match", new Document("$or", List.of(
				new Document("collectorId", collectorId), new Document("submissions.collectorId", collectorId)
			))),
			new Document("$unwind", "$submissions")
		);
		base = new ArrayList<>(base);
		base.add(new Document("$match", new Document("$or", List.of(
			new Document("submissions.collectorId", collectorId),
			new Document("$and", List.of(
				new Document("submissions.collectorId", new Document("$exists", false)),
				new Document("collectorId", collectorId)
			))
		))));
		List<Document> pagePipeline = new ArrayList<>(base);
		pagePipeline.add(new Document("$sort", new Document("submissions.submittedAt", -1)));
		pagePipeline.add(new Document("$skip", pageable.getOffset()));
		pagePipeline.add(new Document("$limit", pageable.getPageSize()));
		pagePipeline.add(new Document("$project", new Document()
			.append("itemId", "$_id").append("taskId", 1).append("operationId", "$submissions.operationId")
			.append("submittedAt", "$submissions.submittedAt").append("durationMillis", "$submissions.durationMillis")
			.append("textPresent", "$submissions.textPresent").append("audioPresent", "$submissions.audioPresent")
			.append("reviewConclusion", "$submissions.reviewConclusion").append("status", 1)));
		List<SubmissionView> rows = new ArrayList<>();
		for (Document document : items.aggregate(pagePipeline)) rows.add(submission(document));
		List<Document> countPipeline = new ArrayList<>(base);
		countPipeline.add(new Document("$count", "total"));
		Document count = items.aggregate(countPipeline).first();
		return new PageImpl<>(rows, pageable, count == null ? 0 : number(count, "total"));
	}

	@Override
	public ReviewerSummary aggregateReviewer(String reviewerId) {
		List<Document> pipeline = List.of(
			new Document("$unwind", "$operations"),
			new Document("$match", new Document("operations.actorUserId", reviewerId)
				.append("operations.type", new Document("$in", List.of(
					"REVIEW_CLAIM", "REVIEW_RELEASE", "REVIEW_APPROVE", "REVIEW_REJECT"
				)))),
			new Document("$sort", new Document("operations.occurredAt", 1)),
			new Document("$group", new Document("_id", "$_id")
				.append("operations", new Document("$push", "$operations")))
		);
		long claims = 0, releases = 0, approvals = 0, rejections = 0, duration = 0, decisions = 0;
		for (Document item : items.aggregate(pipeline)) {
			ArrayDeque<Instant> claimedAt = new ArrayDeque<>();
			@SuppressWarnings("unchecked") List<Document> operations = (List<Document>) item.getOrDefault("operations", List.of());
			for (Document operation : operations) {
				String type = operation.getString("type");
				Instant occurredAt = instant(operation.get("occurredAt"));
				switch (type) {
					case "REVIEW_CLAIM" -> { claims++; if (occurredAt != null) claimedAt.add(occurredAt); }
					case "REVIEW_RELEASE" -> releases++;
					case "REVIEW_APPROVE", "REVIEW_REJECT" -> {
						if ("REVIEW_APPROVE".equals(type)) approvals++; else rejections++;
						if (!claimedAt.isEmpty() && occurredAt != null) {
							duration += Math.max(0, Duration.between(claimedAt.removeFirst(), occurredAt).toMillis());
							decisions++;
						}
					}
					default -> { }
				}
			}
		}
		return new ReviewerSummary(claims, releases, approvals, rejections, decisions == 0 ? 0 : duration / decisions);
	}

	@Override
	public List<CollectorReportTask> findCollectorTasks(String collectorId) {
		List<Document> pipeline = List.of(
			new Document("$match", collectorMatch(collectorId, null)),
			new Document("$group", new Document("_id", "$taskId")
				.append("latestSubmittedAt", new Document("$max", "$latestSubmittedAt"))),
			new Document("$sort", new Document("latestSubmittedAt", -1).append("_id", 1)),
			new Document("$lookup", new Document("from", "tasks")
				.append("localField", "_id").append("foreignField", "_id").append("as", "task")),
			new Document("$unwind", "$task"),
			new Document("$project", new Document("_id", 0)
				.append("taskId", "$_id")
				.append("taskCode", "$task.taskCode")
				.append("taskName", "$task.name")
				.append("latestSubmittedAt", 1))
		);
		List<CollectorReportTask> result = new ArrayList<>();
		for (Document row : items.aggregate(pipeline)) {
			result.add(new CollectorReportTask(
				row.getString("taskId"), row.getString("taskCode"), row.getString("taskName"),
				instant(row.get("latestSubmittedAt"))
			));
		}
		return List.copyOf(result);
	}

	@Override
	public Optional<CollectorTaskReport> collectorTaskReport(String collectorId, String taskId) {
		Document task = tasks.find(new Document("_id", taskId)).first();
		if (task == null) return Optional.empty();
		Document summaryRow = items.aggregate(List.of(
			new Document("$match", collectorMatch(collectorId, taskId)),
			new Document("$group", new Document("_id", null)
				.append("submissionCount", new Document("$sum", 1))
				.append("completedCount", new Document("$sum", new Document("$cond", List.of(
					new Document("$eq", List.of("$status", "COMPLETED")), 1, 0
				))))
				.append("recordingDurationMillis", new Document("$sum", new Document(
					"$ifNull", List.of("$currentResult.audio.durationMillis", 0)
				)))
				.append("referenceAudioDurationMillis", new Document("$sum", new Document(
					"$ifNull", List.of("$referenceAudioDurationMillis", 0)
				)))
				.append("referenceVideoDurationMillis", new Document("$sum", new Document(
					"$ifNull", List.of("$referenceVideoDurationMillis", 0)
				))))
		)).first();
		if (summaryRow == null) return Optional.empty();

		List<Document> dayPipeline = List.of(
			new Document("$match", collectorMatch(collectorId, taskId)),
			new Document("$project", new Document()
				.append("date", new Document("$dateToString", new Document()
					.append("format", "%Y-%m-%d")
					.append("date", "$firstSubmittedAt")
					.append("timezone", "Asia/Shanghai")))
				.append("recordingDurationMillis", new Document(
					"$ifNull", List.of("$currentResult.audio.durationMillis", 0)
				))
				.append("referenceAudioDurationMillis", new Document(
					"$ifNull", List.of("$referenceAudioDurationMillis", 0)
				))
				.append("referenceVideoDurationMillis", new Document(
					"$ifNull", List.of("$referenceVideoDurationMillis", 0)
				))),
			new Document("$group", new Document("_id", "$date")
				.append("submissionCount", new Document("$sum", 1))
				.append("recordingDurationMillis", new Document("$sum", "$recordingDurationMillis"))
				.append("referenceAudioDurationMillis", new Document("$sum", "$referenceAudioDurationMillis"))
				.append("referenceVideoDurationMillis", new Document("$sum", "$referenceVideoDurationMillis"))),
			new Document("$sort", new Document("_id", -1))
		);
		List<CollectorTaskDaySummary> days = new ArrayList<>();
		for (Document row : items.aggregate(dayPipeline)) {
			days.add(new CollectorTaskDaySummary(
				LocalDate.parse(row.getString("_id")),
				number(row, "submissionCount"),
				number(row, "recordingDurationMillis"),
				number(row, "referenceAudioDurationMillis"),
				number(row, "referenceVideoDurationMillis")
			));
		}
		List<CollectorTaskReportItem> recent = findCollectorTaskSubmissions(
			collectorId, taskId, Pageable.ofSize(3)
		).getContent();
		return Optional.of(new CollectorTaskReport(
			taskId,
			task.getString("taskCode"),
			task.getString("name"),
			new CollectorTaskReportSummary(
				number(summaryRow, "submissionCount"),
				number(summaryRow, "completedCount"),
				number(summaryRow, "recordingDurationMillis"),
				number(summaryRow, "referenceAudioDurationMillis"),
				number(summaryRow, "referenceVideoDurationMillis")
			),
			List.copyOf(days),
			List.copyOf(recent)
		));
	}

	@Override
	public Page<CollectorTaskReportItem> findCollectorTaskSubmissions(
		String collectorId, String taskId, Pageable pageable
	) {
		Document match = collectorMatch(collectorId, taskId);
		List<Document> pagePipeline = new ArrayList<>();
		pagePipeline.add(new Document("$match", match));
		pagePipeline.add(new Document("$sort", new Document("latestSubmittedAt", -1).append("itemCode", 1)));
		pagePipeline.add(new Document("$skip", pageable.getOffset()));
		pagePipeline.add(new Document("$limit", pageable.getPageSize()));
		pagePipeline.add(new Document("$project", new Document()
			.append("itemId", "$_id")
			.append("itemCode", 1)
			.append("firstSubmittedAt", 1)
			.append("latestSubmittedAt", 1)
			.append("currentItemStatus", "$status")
			.append("recordingDurationMillis", new Document(
				"$ifNull", List.of("$currentResult.audio.durationMillis", 0)
			))
			.append("referenceAudioDurationMillis", new Document(
				"$ifNull", List.of("$referenceAudioDurationMillis", 0)
			))
			.append("referenceVideoDurationMillis", new Document(
				"$ifNull", List.of("$referenceVideoDurationMillis", 0)
			))
			.append("textPresent", new Document("$and", List.of(
				new Document("$ne", java.util.Arrays.asList(
					new Document("$ifNull", java.util.Arrays.asList("$currentResult.text", null)), null
				)),
				new Document("$ne", List.of("$currentResult.text", ""))
			)))
			.append("audioPresent", new Document("$ne", java.util.Arrays.asList(
				new Document("$ifNull", java.util.Arrays.asList("$currentResult.audio", null)), null
			)))));
		List<CollectorTaskReportItem> result = new ArrayList<>();
		for (Document row : items.aggregate(pagePipeline)) result.add(collectorTaskItem(row));
		Document count = items.aggregate(List.of(
			new Document("$match", match),
			new Document("$count", "total")
		)).first();
		return new PageImpl<>(result, pageable, count == null ? 0 : number(count, "total"));
	}

	private Document collectorMatch(String collectorId, String taskId) {
		Document match = new Document("collectorId", collectorId)
			.append("firstSubmittedAt", new Document("$exists", true))
			.append("status", new Document("$nin", List.of("AVAILABLE", "DISCARDED")));
		if (taskId != null) match.append("taskId", taskId);
		return match;
	}

	private CollectorTaskReportItem collectorTaskItem(Document row) {
		return new CollectorTaskReportItem(
			String.valueOf(row.get("itemId")),
			row.getString("itemCode"),
			instant(row.get("firstSubmittedAt")),
			instant(row.get("latestSubmittedAt")),
			TaskItemStatus.valueOf(row.getString("currentItemStatus")),
			number(row, "recordingDurationMillis"),
			number(row, "referenceAudioDurationMillis"),
			number(row, "referenceVideoDurationMillis"),
			Boolean.TRUE.equals(row.getBoolean("textPresent")),
			Boolean.TRUE.equals(row.getBoolean("audioPresent"))
		);
	}

	private Document countType(Document source, String type) {
		return new Document("$size", new Document("$filter", new Document()
			.append("input", source).append("as", "operation")
			.append("cond", new Document("$eq", List.of("$$operation.type", type)))));
	}

	private Document completedCondition(String collectorId) {
		Document completed = new Document("$eq", List.of("$status", "COMPLETED"));
		return collectorId == null ? completed : new Document("$and", List.of(
			completed, new Document("$eq", List.of("$collectorId", collectorId))
		));
	}

	private SubmissionView submission(Document document) {
		Instant submittedAt = instant(document.get("submittedAt"));
		Object duration = document.get("durationMillis");
		return new SubmissionView(
			String.valueOf(document.get("itemId")), document.getString("taskId"), document.getString("operationId"),
			submittedAt, duration instanceof Number number ? number.longValue() : null,
			Boolean.TRUE.equals(document.getBoolean("textPresent")), Boolean.TRUE.equals(document.getBoolean("audioPresent")),
			document.getString("reviewConclusion"), TaskItemStatus.valueOf(document.getString("status"))
		);
	}

	private Instant instant(Object value) {
		return value instanceof Date date ? date.toInstant() : value instanceof Instant instant ? instant : null;
	}

	private long number(Document document, String field) {
		Object value = document.get(field);
		return value instanceof Number number ? number.longValue() : 0;
	}
}

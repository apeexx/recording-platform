package com.recording.platform.report.store;

import com.recording.platform.report.dto.SubmissionView;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.report.dto.CollectorReportTask;
import com.recording.platform.report.dto.CollectorTaskDaySummary;
import com.recording.platform.report.dto.CollectorTaskReport;
import com.recording.platform.report.dto.CollectorTaskReportItem;
import com.recording.platform.report.dto.CollectorTaskReportSummary;
import com.recording.platform.report.dto.CollectorRankingRow;
import com.recording.platform.report.dto.DashboardItemCounts;
import com.recording.platform.report.dto.DashboardReport;
import com.recording.platform.report.dto.DashboardTaskCounts;
import com.recording.platform.report.dto.DashboardTaskRanking;
import com.recording.platform.report.dto.DashboardTrendPoint;
import com.recording.platform.task.model.TaskItemStatus;
import com.mongodb.client.MongoCollection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
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
	public DashboardReport dashboard(LocalDate fromDate, LocalDate today) {
		Map<String, Long> taskStates = new HashMap<>();
		Map<String, Document> taskById = new HashMap<>();
		for (Document task : tasks.find().projection(new Document(
			"taskCode", 1
		).append("name", 1).append("lifecycle", 1))) {
			String lifecycle = task.getString("lifecycle");
			taskStates.merge(lifecycle == null ? "" : lifecycle, 1L, Long::sum);
			taskById.put(id(task.get("_id")), task);
		}

		Map<String, Long> itemStates = new HashMap<>();
		Map<LocalDate, long[]> daily = new HashMap<>();
		Map<String, long[]> ranking = new HashMap<>();
		HashSet<String> collectors = new HashSet<>();
		ZoneId zone = ZoneId.of("Asia/Shanghai");
		long todayCount = 0;
		for (Document item : items.find().projection(new Document(
			"taskId", 1
		).append("status", 1).append("collectorId", 1).append("firstSubmittedAt", 1)
			.append("currentResult.audio.durationMillis", 1))) {
			String status = item.getString("status");
			itemStates.merge(status == null ? "" : status, 1L, Long::sum);
			String collectorId = item.getString("collectorId");
			if (collectorId != null && !collectorId.isBlank()) collectors.add(collectorId);
			Document result = item.get("currentResult", Document.class);
			Document audio = result == null ? null : result.get("audio", Document.class);
			long duration = audio == null ? 0 : number(audio.get("durationMillis"));
			Object firstValue = item.get("firstSubmittedAt");
			if (firstValue instanceof Date first) {
				LocalDate date = first.toInstant().atZone(zone).toLocalDate();
				if (!date.isBefore(fromDate) && !date.isAfter(today)) {
					long[] point = daily.computeIfAbsent(date, ignored -> new long[2]);
					point[0]++;
					point[1] += duration;
					if (date.equals(today)) todayCount++;
				}
			}
			if (collectorId != null && !"AVAILABLE".equals(status) && !"DISCARDED".equals(status)) {
				long[] values = ranking.computeIfAbsent(item.getString("taskId"), ignored -> new long[3]);
				values[0]++;
				if ("COMPLETED".equals(status)) values[1]++;
				values[2] += duration;
			}
		}
		List<DashboardTrendPoint> trend = new ArrayList<>();
		for (LocalDate date = fromDate; !date.isAfter(today); date = date.plusDays(1)) {
			long[] values = daily.getOrDefault(date, new long[2]);
			trend.add(new DashboardTrendPoint(date, values[0], values[1]));
		}
		List<DashboardTaskRanking> taskRanking = ranking.entrySet().stream()
			.map(entry -> {
				Document task = taskById.get(entry.getKey());
				long[] values = entry.getValue();
				return new DashboardTaskRanking(
					entry.getKey(),
					task == null ? entry.getKey() : task.getString("taskCode"),
					task == null ? "未知任务" : task.getString("name"),
					values[0], values[1], values[2]
				);
			})
			.sorted(java.util.Comparator.comparingLong(DashboardTaskRanking::completedCount).reversed()
				.thenComparing(java.util.Comparator.comparingLong(
					DashboardTaskRanking::submissionCount
				).reversed()))
			.limit(8)
			.toList();
		long taskTotal = taskStates.values().stream().mapToLong(Long::longValue).sum();
		long itemTotal = itemStates.values().stream().mapToLong(Long::longValue).sum();
		return new DashboardReport(
			new DashboardTaskCounts(
				taskTotal, count(taskStates, "DRAFT"), count(taskStates, "RUNNING"),
				count(taskStates, "PAUSED"), count(taskStates, "ENDED")
			),
			new DashboardItemCounts(
				itemTotal, count(itemStates, "AVAILABLE"), count(itemStates, "RECORDING_PENDING"),
				count(itemStates, "REWORK_PENDING"), count(itemStates, "SUBMITTED"),
				count(itemStates, "REVIEW_PENDING"), count(itemStates, "COMPLETED"),
				count(itemStates, "DISCARDED")
			),
			collectors.size(), todayCount, trend, taskRanking, null
		);
	}

	private long count(Map<String, Long> values, String key) {
		return values.getOrDefault(key, 0L);
	}

	private long number(Object value) {
		return value instanceof Number number ? number.longValue() : 0;
	}

	private String id(Object value) {
		return value instanceof ObjectId objectId ? objectId.toHexString() : String.valueOf(value);
	}

	@Override
	public WorkSummary aggregateWork(String collectorId, String taskId) {
		return aggregateWork(collectorId, taskId, null, null);
	}

	@Override
	public WorkSummary aggregateWork(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive
	) {
		List<Document> pipeline = new ArrayList<>();
		Document match = new Document();
		if (collectorId != null) match.append("$or", List.of(
			new Document("collectorId", collectorId),
			new Document("submissions.collectorId", collectorId),
			new Document("operations.actorUserId", collectorId)
		));
		if (taskId != null) match.append("taskId", taskId);
		appendDateRange(match, "firstSubmittedAt", fromInclusive, toExclusive);
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
			.append("effectiveSubmission", new Document("$cond", List.of(effectiveCondition(collectorId), 1, 0)))
			.append("effectiveCompleted", new Document("$cond", List.of(
				new Document("$and", List.of(
					effectiveCondition(collectorId), new Document("$eq", List.of("$status", "COMPLETED"))
				)), 1, 0
			)))
			.append("effectiveRecordingDuration", new Document("$cond", List.of(
				effectiveCondition(collectorId),
				new Document("$ifNull", List.of("$currentResult.audio.durationMillis", 0)), 0
			)))
			.append("referenceAudioDuration", new Document("$cond", List.of(
				effectiveCondition(collectorId),
				new Document("$ifNull", List.of("$referenceAudioDurationMillis", 0)), 0
			)))
			.append("referenceVideoDuration", new Document("$cond", List.of(
				effectiveCondition(collectorId),
				new Document("$ifNull", List.of("$referenceVideoDurationMillis", 0)), 0
			)))
			.append("releaseCount", countType(operations, "RELEASE"))
			.append("discardCount", countTypes(operations, List.of("ADMIN_DISCARD", "COLLECTOR_DISCARD")))));
		pipeline.add(new Document("$group", new Document("_id", null)
			.append("submissionCount", new Document("$sum", "$submissionCount"))
			.append("submissionDuration", new Document("$sum", "$submissionDuration"))
			.append("completed", new Document("$sum", "$completed"))
			.append("currentDuration", new Document("$sum", "$currentDuration"))
			.append("effectiveSubmission", new Document("$sum", "$effectiveSubmission"))
			.append("effectiveCompleted", new Document("$sum", "$effectiveCompleted"))
			.append("effectiveRecordingDuration", new Document("$sum", "$effectiveRecordingDuration"))
			.append("referenceAudioDuration", new Document("$sum", "$referenceAudioDuration"))
			.append("referenceVideoDuration", new Document("$sum", "$referenceVideoDuration"))
			.append("releaseCount", new Document("$sum", "$releaseCount"))
			.append("discardCount", new Document("$sum", "$discardCount"))));
		Document result = items.aggregate(pipeline).first();
		if (result == null) return new WorkSummary(0, 0, 0, 0, 0, 0);
		return new WorkSummary(number(result, "submissionCount"), number(result, "submissionDuration"),
			number(result, "completed"), number(result, "currentDuration"),
			number(result, "releaseCount"), number(result, "discardCount"),
			number(result, "effectiveSubmission"), number(result, "effectiveCompleted"),
			number(result, "effectiveRecordingDuration"), number(result, "referenceAudioDuration"),
			number(result, "referenceVideoDuration"));
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
	public List<CollectorReportTask> findCollectorTasks(String collectorId) {
		List<Document> pipeline = List.of(
			new Document("$match", collectorMatch(collectorId, null)),
			new Document("$group", new Document("_id", "$taskId")
				.append("latestSubmittedAt", new Document("$max", "$latestSubmittedAt"))),
			new Document("$sort", new Document("latestSubmittedAt", -1).append("_id", 1))
		);
		List<CollectorReportTask> result = new ArrayList<>();
		for (Document row : items.aggregate(pipeline)) {
			String taskId = row.getString("_id");
			Document task = findTask(taskId);
			if (task == null) continue;
			result.add(new CollectorReportTask(
				taskId, task.getString("taskCode"), task.getString("name"),
				instant(row.get("latestSubmittedAt"))
			));
		}
		return List.copyOf(result);
	}

	@Override
	public Optional<CollectorTaskReport> collectorTaskReport(String collectorId, String taskId) {
		return collectorTaskReport(collectorId, taskId, null);
	}

	@Override
	public Optional<CollectorTaskReport> collectorTaskReport(
		String collectorId, String taskId, LocalDate date
	) {
		Instant fromInclusive = date == null ? null
			: date.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
		Instant toExclusive = date == null ? null
			: date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
		return collectorTaskReport(collectorId, taskId, fromInclusive, toExclusive);
	}

	@Override
	public Optional<CollectorTaskReport> collectorTaskReport(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive
	) {
		Document task = findTask(taskId);
		if (task == null) return Optional.empty();
		Document summaryRow = items.aggregate(List.of(
			new Document("$match", collectorMatch(collectorId, taskId, fromInclusive, toExclusive)),
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
		if (summaryRow == null) {
			if (fromInclusive == null && toExclusive == null
				|| items.countDocuments(collectorMatch(collectorId, taskId)) == 0) {
				return Optional.empty();
			}
			summaryRow = new Document();
		}

		List<Document> dayPipeline = List.of(
			new Document("$match", collectorMatch(collectorId, taskId, fromInclusive, toExclusive)),
			new Document("$project", new Document()
				.append("date", new Document("$dateToString", new Document()
					.append("format", "%Y-%m-%d")
					.append("date", "$firstSubmittedAt")
					.append("timezone", "Asia/Shanghai")))
				.append("recordingDurationMillis", new Document(
					"$ifNull", List.of("$currentResult.audio.durationMillis", 0)
				))
				.append("completed", new Document("$cond", List.of(
					new Document("$eq", List.of("$status", "COMPLETED")), 1, 0
				)))
				.append("referenceAudioDurationMillis", new Document(
					"$ifNull", List.of("$referenceAudioDurationMillis", 0)
				))
				.append("referenceVideoDurationMillis", new Document(
					"$ifNull", List.of("$referenceVideoDurationMillis", 0)
				))),
			new Document("$group", new Document("_id", "$date")
				.append("submissionCount", new Document("$sum", 1))
				.append("completedCount", new Document("$sum", "$completed"))
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
				number(row, "completedCount"),
				number(row, "recordingDurationMillis"),
				number(row, "referenceAudioDurationMillis"),
				number(row, "referenceVideoDurationMillis")
			));
		}
		List<CollectorTaskReportItem> recent = findCollectorTaskSubmissions(
			collectorId, taskId, fromInclusive, toExclusive, Pageable.ofSize(3)
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
		return findCollectorTaskSubmissions(collectorId, taskId, null, pageable);
	}

	@Override
	public Page<CollectorTaskReportItem> findCollectorTaskSubmissions(
		String collectorId, String taskId, LocalDate date, Pageable pageable
	) {
		Instant fromInclusive = date == null ? null
			: date.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
		Instant toExclusive = date == null ? null
			: date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
		return findCollectorTaskSubmissions(
			collectorId, taskId, fromInclusive, toExclusive, pageable
		);
	}

	@Override
	public Page<CollectorTaskReportItem> findCollectorTaskSubmissions(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive, Pageable pageable
	) {
		Document match = collectorMatch(collectorId, taskId, fromInclusive, toExclusive);
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
		return collectorMatch(collectorId, taskId, null, null);
	}

	private Document collectorMatch(
		String collectorId, String taskId, Instant fromInclusive, Instant toExclusive
	) {
		Document match = new Document("collectorId", collectorId)
			.append("firstSubmittedAt", new Document("$exists", true))
			.append("status", new Document("$nin", List.of("AVAILABLE", "DISCARDED")));
		if (taskId != null) match.append("taskId", taskId);
		appendDateRange(match, "firstSubmittedAt", fromInclusive, toExclusive);
		return match;
	}

	@Override
	public Page<CollectorRankingRow> findCollectorRankings(
		String taskId, Instant fromInclusive, Instant toExclusive, String sortField, Pageable pageable
	) {
		Document match = new Document("taskId", taskId)
			.append("collectorId", new Document("$ne", null))
			.append("firstSubmittedAt", new Document("$exists", true))
			.append("status", new Document("$nin", List.of("AVAILABLE", "DISCARDED")));
		appendDateRange(match, "firstSubmittedAt", fromInclusive, toExclusive);
		Document group = new Document("_id", "$collectorId")
			.append("submissionCount", new Document("$sum", 1))
			.append("completedCount", new Document("$sum", new Document("$cond", List.of(
				new Document("$eq", List.of("$status", "COMPLETED")), 1, 0
			))))
			.append("recordingDurationMillis", new Document("$sum",
				new Document("$ifNull", List.of("$currentResult.audio.durationMillis", 0))))
			.append("referenceAudioDurationMillis", new Document("$sum",
				new Document("$ifNull", List.of("$referenceAudioDurationMillis", 0))))
			.append("referenceVideoDurationMillis", new Document("$sum",
				new Document("$ifNull", List.of("$referenceVideoDurationMillis", 0))));
		List<Document> base = List.of(
			new Document("$match", match),
			new Document("$group", group)
		);
		List<Document> rowsPipeline = new ArrayList<>(base);
		rowsPipeline.add(new Document("$sort", new Document(sortField, -1).append("_id", 1)));
		rowsPipeline.add(new Document("$skip", pageable.getOffset()));
		rowsPipeline.add(new Document("$limit", pageable.getPageSize()));
		List<CollectorRankingRow> rows = new ArrayList<>();
		for (Document row : items.aggregate(rowsPipeline)) {
			rows.add(new CollectorRankingRow(
				row.getString("_id"), null,
				number(row, "submissionCount"), number(row, "completedCount"),
				number(row, "recordingDurationMillis"), number(row, "referenceAudioDurationMillis"),
				number(row, "referenceVideoDurationMillis")
			));
		}
		List<Document> countPipeline = new ArrayList<>(base);
		countPipeline.add(new Document("$count", "total"));
		Document count = items.aggregate(countPipeline).first();
		return new PageImpl<>(rows, pageable, count == null ? 0 : number(count, "total"));
	}

	private Document findTask(String taskId) {
		if (taskId == null) return null;
		Object storedId = ObjectId.isValid(taskId) ? new ObjectId(taskId) : taskId;
		return tasks.find(new Document("_id", storedId)).first();
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

	private Document countTypes(Document source, List<String> types) {
		return new Document("$size", new Document("$filter", new Document()
			.append("input", source).append("as", "operation")
			.append("cond", new Document("$in", List.of("$$operation.type", types)))));
	}

	private Document completedCondition(String collectorId) {
		Document completed = new Document("$eq", List.of("$status", "COMPLETED"));
		return collectorId == null ? completed : new Document("$and", List.of(
			completed, new Document("$eq", List.of("$collectorId", collectorId))
		));
	}

	private Document effectiveCondition(String collectorId) {
		Document condition = new Document("$and", List.of(
			new Document("$ne", List.of("$status", "AVAILABLE")),
			new Document("$ne", List.of("$status", "DISCARDED")),
			new Document("$ne", java.util.Arrays.asList(
				new Document("$ifNull", java.util.Arrays.asList("$firstSubmittedAt", null)), null
			))
		));
		return collectorId == null ? condition : new Document("$and", List.of(
			condition, new Document("$eq", List.of("$collectorId", collectorId))
		));
	}

	private void appendDateRange(
		Document match, String field, Instant fromInclusive, Instant toExclusive
	) {
		if (fromInclusive == null && toExclusive == null) return;
		Document range = new Document();
		if (fromInclusive != null) range.append("$gte", Date.from(fromInclusive));
		if (toExclusive != null) range.append("$lt", Date.from(toExclusive));
		Object existing = match.get(field);
		if (existing instanceof Document document) document.putAll(range);
		else match.append(field, range);
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

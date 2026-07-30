package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TaskItemCsvExportService {
	private static final byte[] UTF8_BOM = new byte[] {
		(byte) 0xef, (byte) 0xbb, (byte) 0xbf
	};
	private static final String[] HEADERS = {
		"任务编号", "任务名称", "条目编号", "来源平台", "脚本来源 ID",
		"状态代码", "状态名称", "采集员 ID", "采集员姓名", "审核员 ID", "审核员姓名",
		"参考文本", "参考音频地址", "参考视频地址",
		"首次提交时间", "最新提交时间", "首次完成时间", "创建时间", "更新时间",
		"当前结果文本", "最终结果文本", "是否包含结果录音",
		"最终录音时长（毫秒）", "参考音频时长（毫秒）", "参考视频时长（毫秒）"
	};

	private final TaskStore tasks;
	private final TaskItemStore items;
	private final IdentityDirectory users;

	public TaskItemCsvExportService(TaskStore tasks, TaskItemStore items, IdentityDirectory users) {
		this.tasks = tasks;
		this.items = items;
		this.users = users;
	}

	public void prepare(String taskId) {
		requireTask(taskId);
	}

	public void write(String taskId, TaskItemFilter filter, OutputStream output) throws IOException {
		TaskRecord task = requireTask(taskId);
		output.write(UTF8_BOM);
		CSVFormat format = CSVFormat.DEFAULT.builder()
			.setHeader(HEADERS)
			.setRecordSeparator("\r\n")
			.get();
		try (
			OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
			CSVPrinter csv = new CSVPrinter(writer, format)
		) {
			int page = 0;
			while (true) {
				List<TaskItem> rows = items.findAllByTaskId(
					taskId,
					filter == null ? TaskItemFilter.all() : filter,
					PageRequest.of(page, 100, Sort.by(Sort.Direction.ASC, "sequence"))
				).getContent();
				Map<String, IdentityUser> identities = identities(rows);
				for (TaskItem item : rows) print(csv, task, item, identities);
				if (rows.size() < 100) break;
				page++;
			}
		}
	}

	private TaskRecord requireTask(String taskId) {
		return tasks.findById(taskId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
	}

	private Map<String, IdentityUser> identities(List<TaskItem> rows) {
		LinkedHashSet<String> ids = rows.stream()
			.flatMap(item -> java.util.stream.Stream.of(item.getCollectorId(), item.getReviewerId()))
			.filter(value -> value != null && !value.isBlank())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.isEmpty()) return Map.of();
		return users.findAllByIdIn(ids).stream()
			.collect(Collectors.toMap(IdentityUser::id, Function.identity()));
	}

	private void print(
		CSVPrinter csv, TaskRecord task, TaskItem item, Map<String, IdentityUser> identities
	) throws IOException {
		TaskItemResult result = item.getCurrentResult();
		String currentText = result == null ? null : result.text();
		String finalText = item.getStatus() == TaskItemStatus.COMPLETED
			? firstNonBlank(item.getReviewFinalAnswer(), currentText) : null;
		csv.printRecord(
			cell(task.getTaskCode()),
			cell(task.getName()),
			cell(item.getItemCode()),
			cell(item.getSourcePlatform()),
			cell(item.getSourceItemId()),
			cell(item.getStatus() == null ? null : item.getStatus().name()),
			cell(statusLabel(item.getStatus())),
			cell(item.getCollectorId()),
			cell(identityName(identities, item.getCollectorId())),
			cell(item.getReviewerId()),
			cell(identityName(identities, item.getReviewerId())),
			cell(item.getReferenceText()),
			cell(safeReferenceUrl(item.getReferenceAudioUrl())),
			cell(safeReferenceUrl(item.getReferenceVideoUrl())),
			cell(instant(item.getFirstSubmittedAt())),
			cell(instant(item.getLatestSubmittedAt())),
			cell(instant(item.getFirstCompletedAt())),
			cell(instant(item.getCreatedAt())),
			cell(instant(item.getUpdatedAt())),
			cell(currentText),
			cell(finalText),
			result != null && result.audio() != null ? "是" : "否",
			result == null || result.audio() == null ? 0 : result.audio().durationMillis(),
			item.getReferenceAudioDurationMillis() == null ? 0 : item.getReferenceAudioDurationMillis(),
			item.getReferenceVideoDurationMillis() == null ? 0 : item.getReferenceVideoDurationMillis()
		);
	}

	private String identityName(Map<String, IdentityUser> identities, String id) {
		IdentityUser user = id == null ? null : identities.get(id);
		return user == null ? null : user.name();
	}

	private String firstNonBlank(String preferred, String fallback) {
		return preferred == null || preferred.isBlank() ? fallback : preferred;
	}

	private String instant(Instant value) {
		return value == null ? null : value.toString();
	}

	private String safeReferenceUrl(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			URI source = URI.create(value);
			return new URI(
				source.getScheme(), null, source.getHost(), source.getPort(),
				source.getPath(), null, null
			).toString();
		} catch (IllegalArgumentException | URISyntaxException exception) {
			return null;
		}
	}

	private String cell(String value) {
		if (value == null) return "";
		return !value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
	}

	private String statusLabel(TaskItemStatus status) {
		if (status == null) return "";
		return switch (status) {
			case AVAILABLE -> "待领取";
			case RECORDING_PENDING -> "待录制";
			case REWORK_PENDING -> "待返修";
			case SUBMITTED -> "已提交";
			case AI_PROCESSING -> "AI 处理中";
			case REVIEW_PENDING -> "待审核";
			case COMPLETED -> "已完成";
			case DISCARDED -> "已废弃";
		};
	}
}

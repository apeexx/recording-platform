package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaCleanupService;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RecordingRetirement;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.recording.platform.task.service.BatchItemCommand;
import com.recording.platform.task.service.BatchItemResult;
import java.util.ArrayList;
import java.util.List;
import com.recording.platform.identity.model.UserRole;

@Service
public class TaskItemActionService {
	private final TaskItemStore items;
	private final TaskPoolService pool;
	private final RecordingMediaStorage storage;
	private final MediaCleanupService cleanup;

	public TaskItemActionService(
		TaskItemStore items,
		TaskPoolService pool,
		RecordingMediaStorage storage,
		MediaCleanupService cleanup
	) {
		this.items = items;
		this.pool = pool;
		this.storage = storage;
		this.cleanup = cleanup;
	}

	public TaskItemActionResult release(
		String itemId,
		String operationId,
		long expectedRevision,
		PlatformPrincipal actor
	) {
		cleanup.retry(itemId, operationId);
		TaskItem item = requireItem(itemId);
		TaskItemResult current = item.getCurrentResult();
		SubmittedRecording audio = current == null ? null : current.audio();
		RecordingRetirement retirement = audio == null ? null : storage.stageRetirement(audio.relativePath());
		TaskItemActionResult result;
		try {
			result = pool.release(itemId, operationId, expectedRevision, actor);
		} catch (RuntimeException exception) {
			rollback(retirement, exception);
			throw exception;
		}
		if (audio != null) {
			String backupRelativePath = retirement.deferCleanup();
			cleanup.scheduleAndTry(
				itemId,
				operationId,
				backupRelativePath == null ? java.util.List.of() : java.util.List.of(backupRelativePath),
				audio.mediaId() == null ? java.util.List.of() : java.util.List.of(audio.mediaId())
			);
		}
		return result;
	}

	public TaskItemActionResult reject(
		String itemId,
		String operationId,
		long expectedRevision,
		String reason,
		PlatformPrincipal actor
	) {
		return pool.reject(itemId, operationId, expectedRevision, reason, actor);
	}

	public List<BatchItemResult> batchRelease(
		String operationId, List<BatchItemCommand> commands, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
		}
		if (operationId == null || operationId.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OPERATION_ID_REQUIRED", "operationId 不能为空");
		}
		if (commands == null || commands.isEmpty() || commands.size() > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_SIZE", "批量操作数量必须为 1 到 100");
		}
		List<BatchItemResult> results = new ArrayList<>();
		String batchId = operationId.trim();
		for (int index = 0; index < commands.size(); index++) {
			BatchItemCommand command = commands.get(index);
			try {
				TaskItemActionResult result = release(
					command.itemId(), batchId + ":" + index, command.expectedRevision(), actor
				);
				results.add(BatchItemResult.success(command.itemId(), result.revision()));
			} catch (ApiException exception) {
				results.add(BatchItemResult.failure(command.itemId(), exception.getCode(), exception.getMessage()));
			}
		}
		return results;
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}

	private void rollback(RecordingRetirement retirement, RuntimeException original) {
		if (retirement == null) return;
		try {
			retirement.rollback();
		} catch (RuntimeException rollbackFailure) {
			original.addSuppressed(rollbackFailure);
		}
	}
}

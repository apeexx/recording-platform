package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAsset;
import com.recording.platform.media.MediaAssetStore;
import com.recording.platform.media.MediaKind;
import com.recording.platform.media.MediaCleanupService;
import com.recording.platform.media.PreparedRecording;
import com.recording.platform.media.RecordingMediaStorage;
import com.recording.platform.media.RecordingReplacement;
import com.recording.platform.media.RecordingRetirement;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.SubmittedRecording;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.SubmitTaskItemCommand;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.task.store.TaskItemStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TaskItemSubmissionService {
	private static final int ITEM_LOCK_STRIPES = 256;
	private final TaskItemStore items;
	private final TaskStore tasks;
	private final TaskPoolService pool;
	private final RecordingMediaStorage storage;
	private final MediaAssetStore assets;
	private final MediaCleanupService cleanup;
	private final Clock clock;
	private final ReentrantLock[] itemLocks = createLocks();

	public TaskItemSubmissionService(
		TaskItemStore items,
		TaskStore tasks,
		TaskPoolService pool,
		RecordingMediaStorage storage,
		MediaAssetStore assets,
		MediaCleanupService cleanup,
		Clock clock
	) {
		this.items = items;
		this.tasks = tasks;
		this.pool = pool;
		this.storage = storage;
		this.assets = assets;
		this.cleanup = cleanup;
		this.clock = clock;
	}

	public TaskItemActionResult submit(
		String itemId,
		SubmitTaskItemForm form,
		MultipartFile audio,
		PlatformPrincipal actor
	) {
		ReentrantLock itemLock = itemLock(itemId);
		itemLock.lock();
		try {
			return submitLocked(itemId, form, audio, actor);
		} finally {
			itemLock.unlock();
		}
	}

	private TaskItemActionResult submitLocked(
		String itemId,
		SubmitTaskItemForm form,
		MultipartFile audio,
		PlatformPrincipal actor
	) {
		TaskItem item = requireItem(itemId);
		if (item.getOperations().stream().anyMatch((operation) -> form.operationId().equals(operation.getOperationId()))) {
			cleanup.retry(itemId, form.operationId());
			return pool.submit(itemId, new SubmitTaskItemCommand(
				form.operationId(), form.assignmentId(), form.expectedRevision(), form.text(), null
			), actor);
		}
		TaskItemResult previous = item.getCurrentResult();
		SubmittedRecording previousAudio = previous == null ? null : previous.audio();
		if (audio == null || audio.isEmpty()) {
			RecordingRetirement retirement = previousAudio == null
				? null : storage.stageRetirement(previousAudio.relativePath());
			TaskItemActionResult result;
			try {
				result = pool.submit(itemId, new SubmitTaskItemCommand(
					form.operationId(), form.assignmentId(), form.expectedRevision(), form.text(), null
				), actor);
			} catch (RuntimeException exception) {
				rollback(retirement, exception);
				throw exception;
			}
			if (previousAudio != null) {
				cleanup.scheduleAndTry(
					itemId,
					form.operationId(),
					single(retirement.deferCleanup()),
					single(previousAudio.mediaId())
				);
			}
			return result;
		}
		TaskRecord task = tasks.findById(item.getTaskId())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在"));
		if (task.getConfiguration() == null) {
			throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFIGURATION_MISSING", "任务配置不存在");
		}
		PreparedRecording prepared = storage.prepare(audio, task.getConfiguration(), task.getTaskCode(), item.getItemCode());
		RecordingReplacement replacement = storage.activate(
			prepared,
			previousAudio == null ? null : previousAudio.relativePath()
		);
		MediaAsset asset = asset(item, prepared.recording());
		TaskItemActionResult result;
		try {
			assets.save(asset);
			result = pool.submit(itemId, new SubmitTaskItemCommand(
				form.operationId(), form.assignmentId(), form.expectedRevision(), form.text(), prepared.recording()
			), actor);
		} catch (RuntimeException exception) {
			replacement.rollback();
			try {
				assets.deleteById(asset.getId());
			} catch (RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
		}
		SubmittedRecording committedAudio = result.result() == null ? null : result.result().audio();
		if (committedAudio == null || !prepared.recording().mediaId().equals(committedAudio.mediaId())) {
			replacement.rollback();
			assets.deleteById(asset.getId());
			return result;
		}
		String backupRelativePath = replacement.deferCleanup();
		if (backupRelativePath != null || previousAudio != null) {
			cleanup.scheduleAndTry(
				itemId,
				form.operationId(),
				single(backupRelativePath),
				single(previousAudio == null ? null : previousAudio.mediaId())
			);
		}
		return result;
	}

	private MediaAsset asset(TaskItem item, SubmittedRecording recording) {
		MediaAsset asset = new MediaAsset();
		asset.setId(recording.mediaId());
		asset.setTaskId(item.getTaskId());
		asset.setItemId(item.getId());
		asset.setKind(MediaKind.RECORDING);
		asset.setRelativePath(recording.relativePath());
		asset.setContentType(recording.format().name().equals("WAV") ? "audio/wav" : "audio/mpeg");
		asset.setSizeBytes(recording.sizeBytes());
		asset.setAudioFormat(recording.format());
		asset.setSampleRate(recording.sampleRate());
		asset.setChannels(recording.channels());
		asset.setDurationMillis(recording.durationMillis());
		asset.setCreatedAt(Instant.now(clock));
		return asset;
	}

	private List<String> single(String value) {
		return value == null || value.isBlank() ? List.of() : List.of(value);
	}

	private void rollback(RecordingRetirement retirement, RuntimeException original) {
		if (retirement == null) return;
		try {
			retirement.rollback();
		} catch (RuntimeException rollbackFailure) {
			original.addSuppressed(rollbackFailure);
		}
	}

	private TaskItem requireItem(String itemId) {
		return items.findById(itemId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_ITEM_NOT_FOUND", "任务条目不存在"));
	}

	private ReentrantLock itemLock(String itemId) {
		return itemLocks[Math.floorMod(itemId.hashCode(), itemLocks.length)];
	}

	private static ReentrantLock[] createLocks() {
		ReentrantLock[] locks = new ReentrantLock[ITEM_LOCK_STRIPES];
		for (int index = 0; index < locks.length; index++) locks[index] = new ReentrantLock();
		return locks;
	}
}

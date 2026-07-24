package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskItemStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MediaAccessService {
	private final MediaAssetStore assets;
	private final TaskItemStore items;
	private final TaskGrantStore grants;
	private final RecordingMediaStorage storage;

	public MediaAccessService(
		MediaAssetStore assets,
		TaskItemStore items,
		TaskGrantStore grants,
		RecordingMediaStorage storage
	) {
		this.assets = assets;
		this.items = items;
		this.grants = grants;
		this.storage = storage;
	}

	public ReadableMedia open(String mediaId, PlatformPrincipal actor) {
		if (actor == null) throw forbidden();
		MediaAsset asset = assets.findById(mediaId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在"));
		if (!canRead(asset, actor)) throw forbidden();
		return readable(asset);
	}

	public ReadableMedia openPublicReference(String mediaId) {
		MediaAsset asset = assets.findById(mediaId)
			.filter(candidate -> candidate.getKind() == MediaKind.REFERENCE_AUDIO
				|| candidate.getKind() == MediaKind.REFERENCE_VIDEO)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "参考媒体不存在"));
		return readable(asset);
	}

	public ReadableMedia openIntegrationRecording(String mediaId, String itemId) {
		MediaAsset asset = assets.findById(mediaId)
			.filter(candidate -> candidate.getKind() == MediaKind.RECORDING)
			.filter(candidate -> itemId.equals(candidate.getItemId()))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "录音媒体不存在"));
		return readable(asset);
	}

	private ReadableMedia readable(MediaAsset asset) {
		Path path = storage.resolve(asset.getRelativePath());
		if (!Files.isRegularFile(path)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_FILE_MISSING", "媒体文件已清理或不存在");
		}
		try {
			return new ReadableMedia(path, safeContentType(asset.getContentType()), Files.size(path));
		} catch (IOException exception) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED", "媒体文件暂时无法读取");
		}
	}

	private boolean canRead(MediaAsset asset, PlatformPrincipal actor) {
		if (actor.role() == UserRole.ADMIN || actor.role() == UserRole.REVIEWER) return true;
		if (actor.role() != UserRole.COLLECTOR) return false;
		if (asset.getItemId() != null) {
			TaskItem item = items.findById(asset.getItemId()).orElse(null);
			if (item != null && actor.userId().equals(item.getCollectorId())) return true;
		}
		return asset.getKind() != MediaKind.RECORDING
			&& grants.findActive(asset.getTaskId(), actor.userId()).isPresent();
	}

	private String safeContentType(String contentType) {
		if (contentType == null || contentType.isBlank() || contentType.contains("\r") || contentType.contains("\n")) {
			return "application/octet-stream";
		}
		return contentType;
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "MEDIA_ACCESS_DENIED", "没有权限读取该媒体");
	}
}

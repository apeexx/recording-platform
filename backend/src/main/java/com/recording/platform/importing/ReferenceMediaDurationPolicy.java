package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.TaskItem;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ReferenceMediaDurationPolicy {
	private static final long MAX_DRIFT_MILLIS = 1_000;

	public Durations validate(TaskItem item, Long audioMillis, Long videoMillis) {
		long audio = validateOne(
			hasText(item.getReferenceAudioUrl()) || hasText(item.getReferenceAudioMediaId()),
			audioMillis,
			item.getReferenceAudioDurationMillis(),
			"AUDIO"
		);
		long video = validateOne(
			hasText(item.getReferenceVideoUrl()) || hasText(item.getReferenceVideoMediaId()),
			videoMillis,
			item.getReferenceVideoDurationMillis(),
			"VIDEO"
		);
		return new Durations(audio, video);
	}

	private long validateOne(boolean sourceExists, Long measured, Long confirmed, String mediaType) {
		long value = measured == null ? 0 : measured;
		if (!sourceExists) {
			if (value != 0) throw error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"REFERENCE_DURATION_NOT_APPLICABLE",
				"不存在对应参考媒体，不能提交媒体时长",
				mediaType
			);
			return 0;
		}
		if (value <= 0) throw error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"REFERENCE_DURATION_REQUIRED",
			"参考媒体尚未加载完成，请稍后重试",
			mediaType
		);
		if (confirmed != null && Math.abs(confirmed - value) > MAX_DRIFT_MILLIS) throw error(
			HttpStatus.CONFLICT,
			"REFERENCE_DURATION_MISMATCH",
			"参考媒体时长与首次确认值不一致，请重新加载作业",
			mediaType
		);
		return confirmed == null ? value : confirmed;
	}

	private ApiException error(HttpStatus status, String code, String message, String mediaType) {
		return new ApiException(status, code, message, Map.of("mediaType", mediaType));
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record Durations(long audioMillis, long videoMillis) { }
}

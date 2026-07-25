package com.recording.platform.importing;

import com.recording.platform.api.ApiException;
import org.springframework.http.HttpStatus;

public record TaskItemSourceBinding(String sourcePlatform, String sourceItemId) {
	public static TaskItemSourceBinding normalize(String sourcePlatform, String sourceItemId) {
		boolean platformMissing = sourcePlatform == null || sourcePlatform.isBlank();
		boolean itemMissing = sourceItemId == null || sourceItemId.isBlank();
		if (platformMissing && itemMissing) return null;
		if (platformMissing || itemMissing) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"SOURCE_BINDING_INVALID",
				"来源平台和来源条目编号必须同时提供"
			);
		}
		return new TaskItemSourceBinding(sourcePlatform.trim(), sourceItemId.trim());
	}

	public static TaskItemSourceBinding normalize(TaskItemSourceBinding binding) {
		return binding == null ? null : normalize(binding.sourcePlatform(), binding.sourceItemId());
	}
}

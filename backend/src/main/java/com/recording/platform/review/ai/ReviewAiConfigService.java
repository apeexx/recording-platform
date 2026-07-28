package com.recording.platform.review.ai;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReviewAiConfigService {
	private static final Set<String> AUDIO_MODELS =
		Set.of("qwen3.5-omni-plus", "qwen3.5-omni-flash");
	private static final Set<String> TEXT_MODELS =
		Set.of("qwen3.5-plus", "qwen3.5-flash");

	private final ReviewAiConfigRepository configs;
	private final TaskStore tasks;
	private final Clock clock;

	public ReviewAiConfigService(ReviewAiConfigRepository configs, TaskStore tasks, Clock clock) {
		this.configs = configs;
		this.tasks = tasks;
		this.clock = clock;
	}

	public ReviewAiConfig get(String taskId, PlatformPrincipal actor) {
		requireReader(actor);
		requireTask(taskId);
		return configs.findById(taskId).orElseGet(() -> ReviewAiDefaults.config(taskId));
	}

	public ReviewAiConfig put(
		String taskId, ReviewAiStageConfig audio, ReviewAiStageConfig text, PlatformPrincipal actor
	) {
		if (actor == null || actor.role() != UserRole.ADMIN) throw forbidden();
		requireTask(taskId);
		validate(audio, AUDIO_MODELS, "音频");
		validate(text, TEXT_MODELS, "文本");
		ReviewAiConfig config = new ReviewAiConfig();
		config.setTaskId(taskId);
		config.setAudio(audio);
		config.setText(text);
		config.setUpdatedBy(actor.userId());
		config.setUpdatedAt(Instant.now(clock));
		return configs.save(config);
	}

	private void validate(ReviewAiStageConfig config, Set<String> models, String label) {
		if (config == null) throw invalid("AI_CONFIG_REQUIRED", label + " AI 配置不能为空");
		if (!models.contains(config.model())) throw invalid("AI_MODEL_INVALID", label + "模型不在允许列表中");
		if (config.prompt() == null || config.prompt().isBlank() || config.prompt().length() > 20000) {
			throw invalid("AI_PROMPT_INVALID", "Prompt 长度必须为 1 到 20000 个字符");
		}
		if (config.temperature() < 0 || config.temperature() >= 2) {
			throw invalid("AI_TEMPERATURE_INVALID", "temperature 必须大于等于 0 且小于 2");
		}
		if (config.topP() <= 0 || config.topP() > 1) {
			throw invalid("AI_TOP_P_INVALID", "topP 必须大于 0 且小于等于 1");
		}
		if (config.maxTokens() < 1 || config.maxTokens() > 8192) {
			throw invalid("AI_MAX_TOKENS_INVALID", "maxTokens 必须为 1 到 8192");
		}
		if (config.timeoutMs() < 5000 || config.timeoutMs() > 600000) {
			throw invalid("AI_TIMEOUT_INVALID", "timeoutMs 必须为 5000 到 600000");
		}
	}

	private void requireReader(PlatformPrincipal actor) {
		if (actor == null || actor.role() != UserRole.ADMIN && actor.role() != UserRole.REVIEWER) {
			throw forbidden();
		}
	}

	private void requireTask(String taskId) {
		if (tasks.findById(taskId).isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
		}
	}

	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行此操作");
	}
}

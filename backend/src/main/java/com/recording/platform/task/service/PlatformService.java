package com.recording.platform.task.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.task.model.PlatformRecord;
import com.recording.platform.task.store.PlatformStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformService {
	private final PlatformStore platforms;
	private final TaskStore tasks;
	private final Clock clock;

	public PlatformService(PlatformStore platforms, TaskStore tasks, Clock clock) {
		this.platforms = platforms;
		this.tasks = tasks;
		this.clock = clock;
	}

	public PlatformRecord create(PlatformCommand command) {
		String code = required(command.code(), "INVALID_PLATFORM_CODE", "平台编码不能为空").toUpperCase(Locale.ROOT);
		if (platforms.findByCode(code).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "PLATFORM_CODE_EXISTS", "平台编码已存在");
		}
		Instant now = Instant.now(clock);
		PlatformRecord platform = new PlatformRecord();
		platform.setCode(code);
		platform.setName(required(command.name(), "INVALID_PLATFORM_NAME", "平台名称不能为空"));
		platform.setDescription(trimToNull(command.description()));
		platform.setActive(command.active() == null || command.active());
		platform.setCreatedAt(now);
		platform.setUpdatedAt(now);
		return platforms.save(platform);
	}

	public PlatformRecord update(String id, PlatformCommand command) {
		PlatformRecord platform = get(id);
		String code = required(command.code(), "INVALID_PLATFORM_CODE", "平台编码不能为空").toUpperCase(Locale.ROOT);
		platforms.findByCode(code).filter((existing) -> !id.equals(existing.getId())).ifPresent((existing) -> {
			throw new ApiException(HttpStatus.CONFLICT, "PLATFORM_CODE_EXISTS", "平台编码已存在");
		});
		platform.setCode(code);
		platform.setName(required(command.name(), "INVALID_PLATFORM_NAME", "平台名称不能为空"));
		platform.setDescription(trimToNull(command.description()));
		if (command.active() != null) platform.setActive(command.active());
		platform.setUpdatedAt(Instant.now(clock));
		return platforms.save(platform);
	}

	public PlatformRecord get(String id) {
		return platforms.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLATFORM_NOT_FOUND", "平台不存在"));
	}

	public PageResponse<PlatformRecord> list(int page, int size) {
		return PageResponse.from(platforms.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
	}

	public void delete(String id) {
		get(id);
		if (tasks.existsByPlatformId(id)) {
			throw new ApiException(HttpStatus.CONFLICT, "PLATFORM_IN_USE", "平台已被任务引用，不能删除");
		}
		platforms.deleteById(id);
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
		return value.trim();
	}
	private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

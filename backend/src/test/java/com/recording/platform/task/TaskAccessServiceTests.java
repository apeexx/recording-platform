package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.GrantStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskAccessService;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TaskAccessServiceTests {
	private TaskAccessService service;
	private InMemoryGrantStore grants;
	private InMemoryAccessRequestStore requests;
	private PlatformPrincipal collector;
	private PlatformPrincipal admin;

	@BeforeEach
	void setUp() {
		TaskStore tasks = org.mockito.Mockito.mock(TaskStore.class);
		UserStore users = org.mockito.Mockito.mock(UserStore.class);
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setLifecycle(TaskLifecycle.RUNNING);
		when(tasks.findById("task-1")).thenReturn(Optional.of(task));
		UserAccount user = new UserAccount();
		user.setId("collector-1");
		user.setRole(UserRole.COLLECTOR);
		user.setStatus(UserStatus.ACTIVE);
		user.setUsername("123456");
		user.setName("采集员");
		user.setPasswordHash("encoded");
		when(users.findById("collector-1")).thenReturn(Optional.of(user));
		grants = new InMemoryGrantStore();
		requests = new InMemoryAccessRequestStore();
		service = new TaskAccessService(
			tasks,
			users,
			grants,
			requests,
			Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC)
		);
		collector = principal("collector-1", "collector", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
		admin = principal("admin-1", "admin", UserRole.ADMIN, SessionType.WEB);
	}

	@Test
	void duplicatePendingRequestReturnsTheExistingRequest() {
		TaskAccessRequest first = service.requestAccess("task-1", collector);
		TaskAccessRequest duplicate = service.requestAccess("task-1", collector);

		assertThat(duplicate.getId()).isEqualTo(first.getId());
		assertThat(requests.data).hasSize(1);
		assertThat(first.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
	}

	@Test
	void approvingIsIdempotentAndRevocationOnlyChangesTheGrantState() {
		TaskAccessRequest request = service.requestAccess("task-1", collector);
		TaskGrant approved = service.approve(request.getId(), admin);
		TaskGrant approvedAgain = service.approve(request.getId(), admin);

		assertThat(approvedAgain.getId()).isEqualTo(approved.getId());
		assertThat(approved.getStatus()).isEqualTo(GrantStatus.ACTIVE);
		assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
			.isEqualTo(AccessRequestStatus.APPROVED);

		TaskGrant revoked = service.revoke("task-1", "collector-1", admin);
		assertThat(revoked.getStatus()).isEqualTo(GrantStatus.REVOKED);

		TaskGrant replayAfterRevocation = service.approve(request.getId(), admin);
		assertThat(replayAfterRevocation.getStatus())
			.as("重放已经批准的申请不得复活后来已撤销的授权")
			.isEqualTo(GrantStatus.REVOKED);
	}

	@Test
	void decisionPathTaskMustMatchTheRequestTask() {
		TaskAccessRequest request = service.requestAccess("task-1", collector);

		assertThatThrownBy(() -> service.approve("task-other", request.getId(), admin))
			.isInstanceOfSatisfying(com.recording.platform.api.ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(404);
				assertThat(exception.getCode()).isEqualTo("ACCESS_REQUEST_NOT_FOUND");
			});
	}

	private PlatformPrincipal principal(String id, String username, UserRole role, SessionType type) {
		return new PlatformPrincipal("session-" + id, id, username, username, role, type, false);
	}

	private static final class InMemoryGrantStore implements TaskGrantStore {
		private final Map<String, TaskGrant> data = new HashMap<>();
		@Override public TaskGrant save(TaskGrant grant) {
			if (grant.getId() == null) grant.setId(UUID.randomUUID().toString());
			data.put(key(grant.getTaskId(), grant.getUserId()), grant);
			return grant;
		}
		@Override public Optional<TaskGrant> findByTaskIdAndUserId(String taskId, String userId) {
			return Optional.ofNullable(data.get(key(taskId, userId)));
		}
		@Override public Optional<TaskGrant> findActive(String taskId, String userId) {
			return findByTaskIdAndUserId(taskId, userId).filter((grant) -> grant.getStatus() == GrantStatus.ACTIVE);
		}
		@Override public Page<TaskGrant> findAllByTaskId(String taskId, Pageable pageable) {
			return new PageImpl<>(data.values().stream().filter((grant) -> taskId.equals(grant.getTaskId())).toList(), pageable, data.size());
		}
		private String key(String taskId, String userId) { return taskId + ":" + userId; }
	}

	private static final class InMemoryAccessRequestStore implements TaskAccessRequestStore {
		private final Map<String, TaskAccessRequest> data = new HashMap<>();
		@Override public TaskAccessRequest save(TaskAccessRequest request) {
			if (request.getId() == null) request.setId(UUID.randomUUID().toString());
			data.put(request.getId(), request);
			return request;
		}
		@Override public Optional<TaskAccessRequest> findById(String id) { return Optional.ofNullable(data.get(id)); }
		@Override public Optional<TaskAccessRequest> findPending(String taskId, String userId) {
			return data.values().stream().filter((request) -> taskId.equals(request.getTaskId())
				&& userId.equals(request.getUserId()) && request.getStatus() == AccessRequestStatus.PENDING).findFirst();
		}
		@Override public Optional<TaskAccessRequest> decideIfPending(
			String requestId,
			AccessRequestStatus status,
			String decidedBy,
			String reason,
			Instant now
		) {
			TaskAccessRequest request = data.get(requestId);
			if (request == null || request.getStatus() != AccessRequestStatus.PENDING) return Optional.empty();
			request.setStatus(status);
			request.setDecidedBy(decidedBy);
			request.setDecisionReason(reason);
			request.setUpdatedAt(now);
			return Optional.of(request);
		}
		@Override public Page<TaskAccessRequest> findAllByTaskId(String taskId, Pageable pageable) {
			return new PageImpl<>(data.values().stream().filter((request) -> taskId.equals(request.getTaskId())).toList(), pageable, data.size());
		}
	}
}

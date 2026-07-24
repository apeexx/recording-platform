package com.recording.platform.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recording.platform.RecordingPlatformBackendApplication;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.importing.ImportJobService;
import com.recording.platform.importing.TaskItemActionService;
import com.recording.platform.importing.TaskItemCreationService;
import com.recording.platform.importing.TaskItemSubmissionService;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.ImportJobStatus;
import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.model.AccessRequestStatus;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskAccessService;
import com.recording.platform.task.service.TaskManagementService;
import com.recording.platform.task.service.TaskQueryService;
import com.recording.platform.task.service.TaskItemActionResult;
import com.recording.platform.task.service.TaskPoolService;
import com.recording.platform.review.service.ReviewService;
import com.recording.platform.task.service.TaskItemAdministrationService;
import com.recording.platform.operation.service.OperationService;
import com.recording.platform.report.service.ReportService;
import com.recording.platform.report.dto.WorkSummary;
import com.recording.platform.api.ApiException;
import com.recording.platform.media.MediaAccessService;
import com.recording.platform.media.ReadableMedia;
import org.springframework.http.HttpStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = RecordingPlatformBackendApplication.class,
	properties = {
		"spring.data.mongodb.auto-index-creation=false",
		"INITIAL_ADMIN_USERNAME=",
		"INITIAL_ADMIN_PASSWORD=",
		"recording.integration.api-key-sha256=13f4867e74e84825ab632700b0ce972d58cd3d3df741e75ac8b0b3711b27e3a4"
	}
)
@AutoConfigureMockMvc
class TaskApiSecurityIntegrationTests {
	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskPoolService taskPoolService;
	@MockitoBean
	private TaskItemSubmissionService submissionService;
	@MockitoBean
	private TaskItemCreationService creationService;
	@MockitoBean
	private TaskItemActionService actionService;
	@MockitoBean
	private ImportJobService importJobService;
	@MockitoBean
	private TaskManagementService taskManagementService;
	@MockitoBean
	private TaskAccessService taskAccessService;
	@MockitoBean
	private TaskQueryService taskQueryService;
	@MockitoBean
	private IdempotencyService idempotencyService;
	@MockitoBean
	private ReviewService reviewService;
	@MockitoBean
	private TaskItemAdministrationService administrationService;
	@MockitoBean
	private OperationService operationService;
	@MockitoBean
	private ReportService reportService;
	@MockitoBean
	private MediaAccessService mediaAccessService;

	@BeforeEach
	void executeControllerIdempotencyMutations() {
		lenient().when(idempotencyService.execute(any(), anyString(), anyString(), any(Class.class), any()))
			.thenAnswer((invocation) -> ((Supplier<?>) invocation.getArgument(4)).get());
	}

	@Test
	void publicReferenceMediaDoesNotRequireSessionButRecordingMediaStillDoes() throws Exception {
		Path media = Files.createTempFile("public-reference-", ".wav");
		Files.write(media, new byte[] {1, 2, 3});
		when(mediaAccessService.openPublicReference("media-reference"))
			.thenReturn(new ReadableMedia(media, "audio/wav", 3));

		mockMvc.perform(get("/api/media/public/reference/media-reference"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/media/media-recording"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void collectorBearerWritesSkipCookieCsrfButWebStyleRequestsDoNot() throws Exception {
		PlatformPrincipal collector = principal("collector-1", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
		TaskItem claimed = new TaskItem();
		claimed.setId("item-1");
		claimed.setStatus(TaskItemStatus.RECORDING_PENDING);
		when(taskPoolService.start("task-1", collector)).thenReturn(claimed);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(collector, null, "ROLE_COLLECTOR");

		mockMvc.perform(post("/api/tasks/task-1/items/start")
				.with(authentication(authentication))
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-miniprogram-token")
				.header("Idempotency-Key", "start-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("item-1"));
		mockMvc.perform(post("/api/tasks/task-1/items/start")
				.with(authentication(authentication))
				.header("Idempotency-Key", "start-2"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
	}

	@Test
	void integrationItemEndpointUsesOnlyItsDedicatedApiKeyWithoutCsrf() throws Exception {
		TaskItem created = new TaskItem();
		created.setId("item-integration");
		created.setTaskId("task-1");
		created.setItemCode("T000001-0000001");
		created.setStatus(TaskItemStatus.AVAILABLE);
		created.setCreatedAt(Instant.parse("2026-07-24T00:00:00Z"));
		when(creationService.addIntegration(anyString(), any(), anyString())).thenReturn(created);

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("X-API-Key", "test-integration-key")
				.header("Idempotency-Key", "external-add-1")
				.header("X-Request-Id", "request-external-add-1")
				.contentType("application/json")
				.content("{\"referenceText\":\"参考文字\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("X-Request-Id", "request-external-add-1"))
			.andExpect(jsonPath("$.itemId").value("item-integration"))
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.itemCode").value("T000001-0000001"))
			.andExpect(jsonPath("$.status").value("AVAILABLE"))
			.andExpect(jsonPath("$.createdAt").value("2026-07-24T00:00:00Z"));

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("Idempotency-Key", "external-add-2")
				.contentType("application/json")
				.content("{\"referenceText\":\"参考文字\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_INTEGRATION_API_KEY"));

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("X-API-Key", "wrong-key")
				.header("Idempotency-Key", "external-add-3")
				.contentType("application/json")
				.content("{\"referenceText\":\"参考文字\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_INTEGRATION_API_KEY"))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("wrong-key")
			)));

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.cookie(new Cookie("REC_WEB_SESSION", "test-web-token"))
				.header("Idempotency-Key", "external-add-4")
				.contentType("application/json")
				.content("{\"referenceText\":\"参考文字\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_INTEGRATION_API_KEY"));
	}

	@Test
	void integrationItemEndpointRejectsUnknownFieldsAndMalformedJsonAsBadRequests() throws Exception {
		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("X-API-Key", "test-integration-key")
				.header("Idempotency-Key", "external-unknown-field")
				.contentType("application/json")
				.content("{\"referenceText\":\"参考文字\",\"externalItemId\":\"source-1\"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("X-API-Key", "test-integration-key")
				.header("Idempotency-Key", "external-malformed-json")
				.contentType("application/json")
				.content("{\"referenceText\":"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/integrations/tasks/task-1/items")
				.header("X-API-Key", "test-integration-key")
				.header("Idempotency-Key", "external-null-json")
				.contentType("application/json")
				.content("null"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void integrationApiKeyCannotAuthenticateOtherApiRoutes() throws Exception {
		mockMvc.perform(get("/api/admin/users")
				.header("X-API-Key", "test-integration-key"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void multipartSubmissionAndImportUseTheirDocumentedRoleAndStatusContracts() throws Exception {
		PlatformPrincipal collector = principal("collector-1", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
		TaskItemActionResult submitted = new TaskItemActionResult(
			"item-1", TaskItemStatus.REVIEW_PENDING, 2, "assignment-1", null
		);
		when(submissionService.submit(anyString(), any(), any(), any())).thenReturn(submitted);
		MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] {1, 2, 3});

		mockMvc.perform(multipart("/api/task-items/item-1/submit")
				.file(audio)
				.param("operationId", "submit-1")
				.param("assignmentId", "assignment-1")
				.param("expectedRevision", "1")
				.with(authentication(new TestingAuthenticationToken(collector, null, "ROLE_COLLECTOR")))
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-miniprogram-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REVIEW_PENDING"));

		ImportJob job = new ImportJob();
		job.setId(UUID.randomUUID().toString());
		job.setStatus(ImportJobStatus.PENDING);
		when(importJobService.create(anyString(), anyString(), any(), any())).thenReturn(job);
		MockMultipartFile csv = new MockMultipartFile("file", "items.csv", "text/csv", "header".getBytes());
		mockMvc.perform(multipart("/api/import-jobs")
				.file(csv)
				.param("taskId", "task-1")
				.header("Idempotency-Key", "import-1")
				.with(user("admin").roles("ADMIN"))
				.with(csrf()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.importJobId").value(job.getId()));
	}

	@Test
	void adminReleaseStillRequiresCsrfWhileCollectorBearerReleaseDoesNot() throws Exception {
		TaskItemActionResult released = new TaskItemActionResult("item-1", TaskItemStatus.AVAILABLE, 3, null, null);
		when(actionService.release(anyString(), anyString(), any(Long.class), any())).thenReturn(released);
		mockMvc.perform(post("/api/task-items/item-1/release")
				.with(user("admin").roles("ADMIN"))
				.cookie(new Cookie("REC_WEB_SESSION", "test-web-token"))
				.header(HttpHeaders.AUTHORIZATION, "Bearer injected-token")
				.contentType("application/json")
				.content("{\"operationId\":\"release-1\",\"expectedRevision\":2}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/task-items/item-1/release")
				.with(user("admin").roles("ADMIN"))
				.contentType("application/json")
				.content("{\"operationId\":\"release-1\",\"expectedRevision\":2}"))
			.andExpect(status().isForbidden());

		PlatformPrincipal collector = principal("collector-1", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
		mockMvc.perform(post("/api/task-items/item-1/release")
				.with(authentication(new TestingAuthenticationToken(collector, null, "ROLE_COLLECTOR")))
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-miniprogram-token")
				.contentType("application/json")
				.content("{\"operationId\":\"release-1\",\"expectedRevision\":2}"))
			.andExpect(status().isOk());
	}

	@Test
	void taskLifecycleAndAccessApprovalRoutesUseTheRoleSpecificContracts() throws Exception {
		TaskRecord task = new TaskRecord();
		task.setId("task-1");
		task.setLifecycle(TaskLifecycle.DRAFT);
		when(taskManagementService.create(any())).thenReturn(task);
		String body = """
			{
			  "name":"朗读任务",
			  "configuration":{"referenceTypes":["TEXT"],"resultType":"AUDIO","recordingFormat":"WAV","sampleRates":[16000],"channels":1,"minDurationMillis":1000,"maxDurationMillis":60000}
			}
			""";
		mockMvc.perform(post("/api/tasks")
				.with(user("admin").roles("ADMIN"))
				.with(csrf())
				.header("Idempotency-Key", "task-create-1")
				.contentType("application/json")
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value("task-1"));
		mockMvc.perform(post("/api/tasks")
				.with(user("collector").roles("COLLECTOR"))
				.with(csrf())
				.header("Idempotency-Key", "task-create-2")
				.contentType("application/json")
				.content(body))
			.andExpect(status().isForbidden());

		PlatformPrincipal collector = principal("collector-1", UserRole.COLLECTOR, SessionType.MINIPROGRAM);
		TaskAccessRequest request = new TaskAccessRequest();
		request.setId("request-1");
		request.setStatus(AccessRequestStatus.PENDING);
		when(taskAccessService.requestAccess("task-1", collector)).thenReturn(request);
		mockMvc.perform(post("/api/tasks/task-1/access-requests")
				.with(authentication(new TestingAuthenticationToken(collector, null, "ROLE_COLLECTOR")))
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-miniprogram-token")
				.header("Idempotency-Key", "access-request-1"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value("request-1"));

		mockMvc.perform(post("/api/tasks/task-1/access-requests/request-1/approve")
				.with(user("admin").roles("ADMIN"))
				.with(csrf())
				.header("Idempotency-Key", "access-approve-1"))
			.andExpect(status().isOk());
	}

	@Test
	void reviewerCanRejectButCannotUseCollectorStartEndpoint() throws Exception {
		TaskItemActionResult rejected = new TaskItemActionResult(
			"item-1", TaskItemStatus.RECORDING_PENDING, 3, "assignment-1", null
		);
		when(actionService.reject(anyString(), anyString(), any(Long.class), anyString(), any())).thenReturn(rejected);
		mockMvc.perform(post("/api/task-items/item-1/reject")
				.with(user("reviewer").roles("REVIEWER"))
				.with(csrf())
				.contentType("application/json")
				.content("{\"operationId\":\"reject-1\",\"expectedRevision\":2,\"reason\":\"噪音过大\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RECORDING_PENDING"));
		mockMvc.perform(post("/api/tasks/task-1/items/start")
				.with(user("reviewer").roles("REVIEWER"))
				.with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void reviewAndAdministrationRoutesEnforceTheirRoleBoundaries() throws Exception {
		TaskItem claimed = new TaskItem();
		claimed.setId("item-review");
		claimed.setStatus(TaskItemStatus.REVIEW_PENDING);
		when(reviewService.claim(anyString(), anyString(), any())).thenReturn(claimed);

		mockMvc.perform(post("/api/reviews/tasks/task-1/claim")
				.with(user("reviewer").roles("REVIEWER"))
				.with(csrf())
				.header("Idempotency-Key", "claim-review-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("item-review"));
		mockMvc.perform(post("/api/reviews/tasks/task-1/claim")
				.with(user("collector").roles("COLLECTOR"))
				.with(csrf())
				.header("Idempotency-Key", "claim-review-2"))
			.andExpect(status().isForbidden());

		when(administrationService.batchDiscard(anyString(), any(), any())).thenReturn(List.of());
		String batch = """
			{"operationId":"batch-1","items":[{"itemId":"item-1","expectedRevision":2}]}
			""";
		mockMvc.perform(post("/api/task-items/batch/discard")
				.with(user("admin").roles("ADMIN"))
				.with(csrf())
				.contentType("application/json")
				.content(batch))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/task-items/batch/discard")
				.with(user("reviewer").roles("REVIEWER"))
				.with(csrf())
				.contentType("application/json")
				.content(batch))
			.andExpect(status().isForbidden());
	}

	@Test
	void operationAndReportRoutesEnforceReadRoleBoundaries() throws Exception {
		when(reportService.task(anyString(), any())).thenReturn(new WorkSummary(0, 0, 0, 0, 0, 0));

		mockMvc.perform(get("/api/reports/tasks")
				.with(user("admin").roles("ADMIN"))
				.param("taskId", "task-1"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/reports/tasks")
				.with(user("reviewer").roles("REVIEWER"))
				.param("taskId", "task-1"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/operations")
				.with(user("collector").roles("COLLECTOR")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/reports/me")
				.with(user("collector").roles("COLLECTOR")))
			.andExpect(status().isOk());
	}

	@Test
	void removedTaskVersionsRouteReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/tasks/task-private/versions")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isNotFound());
	}

	private PlatformPrincipal principal(String id, UserRole role, SessionType sessionType) {
		return new PlatformPrincipal("session-" + id, id, id, id, role, sessionType, false);
	}
}

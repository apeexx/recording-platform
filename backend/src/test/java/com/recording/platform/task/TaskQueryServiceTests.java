package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import com.recording.platform.task.model.TaskAccessRequest;
import com.recording.platform.task.model.TaskGrant;
import com.recording.platform.task.model.TaskLifecycle;
import com.recording.platform.task.model.TaskRecord;
import com.recording.platform.task.service.TaskQueryService;
import com.recording.platform.task.store.TaskAccessRequestStore;
import com.recording.platform.task.store.TaskGrantStore;
import com.recording.platform.task.store.TaskStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class TaskQueryServiceTests {
	@Test
	void collectorSeesVisibleTasksWithActivePendingAndNonePermissionStates() {
		TaskStore tasks=mock(TaskStore.class);TaskGrantStore grants=mock(TaskGrantStore.class);TaskAccessRequestStore requests=mock(TaskAccessRequestStore.class);
		TaskRecord active=task("t1"),pending=task("t2"),none=task("t3");
		when(tasks.findAllCollectorVisible(PageRequest.of(0,20))).thenReturn(new PageImpl<>(List.of(active,pending,none)));
		when(grants.findActive("t1","u1")).thenReturn(Optional.of(new TaskGrant()));
		when(requests.findPending("t2","u1")).thenReturn(Optional.of(new TaskAccessRequest()));
		TaskQueryService service=new TaskQueryService(tasks,grants,requests);
		PlatformPrincipal actor=new PlatformPrincipal("s","u1","U1","张三",UserRole.COLLECTOR,SessionType.MINIPROGRAM,false);

		assertThat(service.list(actor,0,20).items()).extracting(TaskQueryService.TaskView::permissionStatus)
			.containsExactly("ACTIVE","PENDING","NONE");
	}

	private TaskRecord task(String id){TaskRecord task=new TaskRecord();task.setId(id);task.setTaskCode(id);task.setName(id);task.setLifecycle(TaskLifecycle.RUNNING);return task;}
}

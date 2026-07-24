package com.recording.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.importing.ImportJobController;
import com.recording.platform.integration.IntegrationTaskItemController;
import com.recording.platform.task.controller.TaskAccessController;
import com.recording.platform.task.controller.TaskController;
import com.recording.platform.task.controller.TaskItemsController;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

class MutationIdempotencyContractTests {
	@Test
	void everyTaskTwoMutationWithoutBodyOperationIdRequiresIdempotencyKey() {
		assertHeader(TaskController.class, "create", "update", "publish", "pause", "resume", "end");
		assertHeader(TaskAccessController.class, "request", "approve", "reject", "grant", "revoke");
		assertHeader(TaskItemsController.class, "add", "start");
		assertHeader(ImportJobController.class, "create", "retry");
		assertHeader(IntegrationTaskItemController.class, "add");
	}

	private void assertHeader(Class<?> controller, String... methodNames) {
		for (String methodName : methodNames) {
			List<Method> methods = java.util.Arrays.stream(controller.getDeclaredMethods())
				.filter((method) -> method.getName().equals(methodName))
				.toList();
			assertThat(methods).as(controller.getSimpleName() + "." + methodName).hasSize(1);
			assertThat(java.util.Arrays.stream(methods.get(0).getParameters()))
				.as(controller.getSimpleName() + "." + methodName + " Idempotency-Key")
				.anySatisfy((parameter) -> {
					RequestHeader header = parameter.getAnnotation(RequestHeader.class);
					assertThat(header).isNotNull();
					assertThat(header.value()).isEqualTo("Idempotency-Key");
					assertThat(header.required()).isTrue();
				});
		}
	}
}

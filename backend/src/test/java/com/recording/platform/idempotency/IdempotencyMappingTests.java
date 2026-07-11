package com.recording.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

class IdempotencyMappingTests {
	@Test
	void actorActionAndOperationKeyArePersistentlyUnique() {
		Document document = IdempotencyRecord.class.getAnnotation(Document.class);
		assertThat(document).isNotNull();
		assertThat(document.collection()).isEqualTo("idempotency_records");
		CompoundIndexes indexes = IdempotencyRecord.class.getAnnotation(CompoundIndexes.class);
		assertThat(Arrays.stream(indexes.value())).anySatisfy((index) -> {
			assertThat(index.unique()).isTrue();
			assertThat(index.def()).contains("actorUserId").contains("action").contains("operationKey");
		});
	}
}

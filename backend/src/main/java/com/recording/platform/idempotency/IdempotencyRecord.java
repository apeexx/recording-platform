package com.recording.platform.idempotency;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "idempotency_records")
@CompoundIndexes({
	@CompoundIndex(
		name = "unique_actor_action_operation",
		def = "{'actorUserId': 1, 'action': 1, 'operationKey': 1}",
		unique = true
	),
	@CompoundIndex(name = "idempotency_updated", def = "{'updatedAt': 1}")
})
public class IdempotencyRecord {
	@Id
	private String id;
	@Version
	private Long version;
	private String actorUserId;
	private String action;
	private String operationKey;
	private IdempotencyStatus status;
	private String responseJson;
	private Instant createdAt;
	private Instant updatedAt;
}

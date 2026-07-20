package com.recording.platform.identity.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "sessions")
@CompoundIndexes({
	@CompoundIndex(name = "session_user_status", def = "{'userId': 1, 'status': 1}"),
	@CompoundIndex(
		name = "unique_active_web_session",
		def = "{'userId': 1, 'type': 1}",
		unique = true,
		partialFilter = "{'status': 'ACTIVE', 'type': 'WEB'}"
	),
	@CompoundIndex(
		name = "unique_active_miniprogram_session",
		def = "{'userId': 1, 'type': 1}",
		unique = true,
		partialFilter = "{'status': 'ACTIVE', 'type': 'MINIPROGRAM'}"
	)
})
public class SessionRecord {
	@Id
	private String id;

	@Version
	private Long version;

	private String userId;

	@Indexed(unique = true)
	private String tokenHash;

	private SessionType type;
	private SessionStatus status;
	private String replacedSessionId;
	private Instant createdAt;
	private Instant lastAccessAt;

	@Indexed(expireAfter = "0s")
	private Instant expiresAt;
}

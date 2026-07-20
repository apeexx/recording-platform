package com.recording.platform.identity.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "web_users")
public class WebUser {
	@Id
	private String id;

	@Version
	private Long version;

	@Indexed(unique = true)
	private String username;

	private String name;
	private String passwordHash;
	private UserRole role;
	private UserStatus status;
	private boolean firstPasswordChangeRequired;
	private Instant createdAt;
	private Instant updatedAt;
}

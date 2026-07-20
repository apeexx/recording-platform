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
@Document(collection = "miniprogram_users")
@CompoundIndexes({
	@CompoundIndex(
		name = "unique_wechat_identity",
		def = "{'wechatAppId': 1, 'wechatOpenId': 1}",
		unique = true,
		sparse = true
	)
})
public class MiniProgramUser {
	@Id
	private String id;

	@Version
	private Long version;

	@Indexed(unique = true, sparse = true)
	private String account;

	private String name;
	private String passwordHash;
	private UserStatus status;
	private String wechatAppId;
	private String wechatOpenId;
	private String avatarPath;
	private String avatarContentType;
	private Instant avatarUpdatedAt;
	private Instant createdAt;
	private Instant updatedAt;
}

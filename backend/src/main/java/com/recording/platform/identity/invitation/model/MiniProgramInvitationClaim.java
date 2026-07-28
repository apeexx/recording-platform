package com.recording.platform.identity.invitation.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "miniprogram_invitation_claims")
public class MiniProgramInvitationClaim {
	@Id
	private String identityHash;
	private String invitationId;
	private String userId;
	private Instant createdAt;
	private Instant completedAt;
}

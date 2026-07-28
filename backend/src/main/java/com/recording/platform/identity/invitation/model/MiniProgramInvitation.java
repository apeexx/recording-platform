package com.recording.platform.identity.invitation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "miniprogram_invitation_codes")
public class MiniProgramInvitation {
	@Id
	private String id;
	@Version
	private Long version;
	@Indexed(unique = true)
	private String codeHash;
	private String codeSuffix;
	private String name;
	private String note;
	private int maxUses;
	private int usedCount;
	private InvitationStatus status;
	private List<String> redemptionIdentityHashes = new ArrayList<>();
	private String createdByUserId;
	private String createdByName;
	private Instant createdAt;
	private String disabledByUserId;
	private String disabledByName;
	private Instant disabledAt;
}

package com.recording.platform.identity.controller;

import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.dto.CreateMiniProgramInvitationRequest;
import com.recording.platform.identity.dto.MiniProgramInvitationCreatedResponse;
import com.recording.platform.identity.invitation.MiniProgramInvitationService;
import com.recording.platform.identity.invitation.MiniProgramInvitationView;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/miniprogram-invitations")
public class MiniProgramInvitationController {
	private final MiniProgramInvitationService invitations;
	private final IdempotencyService idempotency;

	public MiniProgramInvitationController(
		MiniProgramInvitationService invitations,
		IdempotencyService idempotency
	) {
		this.invitations = invitations;
		this.idempotency = idempotency;
	}

	@GetMapping
	public PageResponse<MiniProgramInvitationView> list(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		return invitations.list(page, size);
	}

	@PostMapping
	public MiniProgramInvitationCreatedResponse create(
		@Valid @RequestBody CreateMiniProgramInvitationRequest request,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return MiniProgramInvitationCreatedResponse.from(invitations.create(
			request.name(), request.note(), request.maxUses(), actor
		));
	}

	@PostMapping("/{invitationId}/disable")
	public MiniProgramInvitationView disable(
		@PathVariable String invitationId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		Authentication authentication,
		@AuthenticationPrincipal PlatformPrincipal actor
	) {
		return idempotency.execute(
			authentication,
			"miniprogram-invitation:disable:" + invitationId,
			idempotencyKey,
			MiniProgramInvitationView.class,
			() -> invitations.disable(invitationId, actor)
		);
	}
}

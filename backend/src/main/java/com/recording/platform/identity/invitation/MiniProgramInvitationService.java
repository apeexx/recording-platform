package com.recording.platform.identity.invitation;

import com.recording.platform.api.ApiException;
import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.invitation.model.InvitationClaimResult;
import com.recording.platform.identity.invitation.model.InvitationEffectiveStatus;
import com.recording.platform.identity.invitation.model.InvitationReservationResult;
import com.recording.platform.identity.invitation.model.InvitationStatus;
import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationClaimStore;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationStore;
import com.recording.platform.security.PlatformPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MiniProgramInvitationService {
	private static final int MAX_GENERATION_ATTEMPTS = 5;
	private final MiniProgramInvitationStore invitations;
	private final MiniProgramInvitationClaimStore claims;
	private final MiniProgramInvitationCodeCodec codes;
	private final Clock clock;

	public MiniProgramInvitationService(
		MiniProgramInvitationStore invitations,
		MiniProgramInvitationClaimStore claims,
		MiniProgramInvitationCodeCodec codes,
		Clock clock
	) {
		this.invitations = invitations;
		this.claims = claims;
		this.codes = codes;
		this.clock = clock;
	}

	public MiniProgramInvitationAdmission authorizeFirstLogin(String invitationCode, String appId, String openId) {
		if (invitationCode == null || invitationCode.isBlank()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_REQUIRED", "当前微信尚未加入使用范围，请输入邀请码");
		}
		String normalized = codes.normalize(invitationCode);
		if (normalized == null || !normalized.matches("[A-HJ-NP-Z2-9]{12}")) throw invalidCode();
		MiniProgramInvitation invitation = invitations.findByCodeHash(codes.hash(normalized))
			.orElseThrow(this::invalidCode);
		String identityHash = codes.identityHash(appId, openId);
		Instant now = Instant.now(clock);
		InvitationClaimResult claim = claims.claim(identityHash, invitation.getId(), now);
		if (claim == InvitationClaimResult.OTHER_INVITATION) throw invalidCode();
		InvitationReservationResult reservation = invitations.reserve(invitation.getId(), identityHash, now);
		if (reservation == InvitationReservationResult.UNAVAILABLE) {
			claims.release(identityHash, invitation.getId());
			throw invalidCode();
		}
		return new MiniProgramInvitationAdmission(
			invitation.getId(),
			invitation.getName(),
			invitation.getCodeSuffix(),
			now
		);
	}

	public void completeRedemption(String appId, String openId, String userId) {
		claims.complete(codes.identityHash(appId, openId), userId, Instant.now(clock));
	}

	public MiniProgramInvitationCreated create(
		String name,
		String note,
		int maxUses,
		PlatformPrincipal actor
	) {
		String normalizedName = requiredText(name, 64, "INVITATION_NAME_INVALID", "邀请码名称不能为空且不能超过 64 个字符");
		String normalizedNote = optionalText(note, 200, "INVITATION_NOTE_INVALID", "邀请码备注不能超过 200 个字符");
		if (maxUses < 1 || maxUses > 1000) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVITATION_MAX_USES_INVALID", "邀请码使用次数必须为 1 到 1000");
		}
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			String rawCode = codes.generate();
			String normalizedCode = codes.normalize(rawCode);
			MiniProgramInvitation invitation = new MiniProgramInvitation();
			invitation.setId(UUID.randomUUID().toString());
			invitation.setName(normalizedName);
			invitation.setNote(normalizedNote);
			invitation.setCodeHash(codes.hash(normalizedCode));
			invitation.setCodeSuffix(normalizedCode.substring(normalizedCode.length() - 4));
			invitation.setMaxUses(maxUses);
			invitation.setUsedCount(0);
			invitation.setStatus(InvitationStatus.ACTIVE);
			invitation.setCreatedByUserId(actor.userId());
			invitation.setCreatedByName(actorName(actor));
			invitation.setCreatedAt(Instant.now(clock));
			try {
				MiniProgramInvitation saved = invitations.save(invitation);
				return new MiniProgramInvitationCreated(view(saved), rawCode);
			} catch (DuplicateKeyException exception) {
				if (attempt == MAX_GENERATION_ATTEMPTS - 1) {
					throw new ApiException(HttpStatus.CONFLICT, "INVITATION_CODE_GENERATION_FAILED", "邀请码生成冲突，请重试");
				}
			}
		}
		throw new ApiException(HttpStatus.CONFLICT, "INVITATION_CODE_GENERATION_FAILED", "邀请码生成冲突，请重试");
	}

	public PageResponse<MiniProgramInvitationView> list(int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		return PageResponse.from(invitations.findAll(PageRequest.of(
			safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")
		)).map(this::view));
	}

	public MiniProgramInvitationView disable(String id, PlatformPrincipal actor) {
		Instant now = Instant.now(clock);
		MiniProgramInvitation invitation = invitations.disable(id, actor.userId(), actorName(actor), now)
			.orElseGet(() -> invitations.findById(id).orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "INVITATION_CODE_NOT_FOUND", "邀请码不存在"
			)));
		return view(invitation);
	}

	private MiniProgramInvitationView view(MiniProgramInvitation invitation) {
		int remaining = Math.max(0, invitation.getMaxUses() - invitation.getUsedCount());
		InvitationEffectiveStatus effective = invitation.getStatus() == InvitationStatus.DISABLED
			? InvitationEffectiveStatus.DISABLED
			: remaining == 0 ? InvitationEffectiveStatus.EXHAUSTED : InvitationEffectiveStatus.ACTIVE;
		return new MiniProgramInvitationView(
			invitation.getId(), invitation.getName(), invitation.getNote(), invitation.getCodeSuffix(),
			invitation.getMaxUses(), invitation.getUsedCount(), remaining, effective,
			invitation.getCreatedByUserId(), invitation.getCreatedByName(), invitation.getCreatedAt(),
			invitation.getDisabledByUserId(), invitation.getDisabledByName(), invitation.getDisabledAt()
		);
	}

	private ApiException invalidCode() {
		return new ApiException(
			HttpStatus.FORBIDDEN,
			"INVITATION_CODE_INVALID",
			"邀请码无效或已失效，请联系管理员"
		);
	}

	private String requiredText(String value, int maximum, String code, String message) {
		String normalized = value == null ? null : value.trim();
		if (normalized == null || normalized.isEmpty() || normalized.length() > maximum) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
		}
		return normalized;
	}

	private String optionalText(String value, int maximum, String code, String message) {
		if (value == null || value.isBlank()) return null;
		return requiredText(value, maximum, code, message);
	}

	private String actorName(PlatformPrincipal actor) {
		if (actor.name() != null && !actor.name().isBlank()) return actor.name();
		if (actor.username() != null && !actor.username().isBlank()) return actor.username();
		return actor.userId();
	}
}

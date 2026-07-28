package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.invitation.MiniProgramInvitationCodeCodec;
import com.recording.platform.identity.invitation.MiniProgramInvitationService;
import com.recording.platform.identity.invitation.model.InvitationClaimResult;
import com.recording.platform.identity.invitation.model.InvitationReservationResult;
import com.recording.platform.identity.invitation.model.MiniProgramInvitation;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationClaimStore;
import com.recording.platform.identity.invitation.store.MiniProgramInvitationStore;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.security.PlatformPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MiniProgramInvitationServiceTests {
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC);

	@Test
	void generatedCodeIsReadableAndNormalizationIgnoresCaseSpacesAndHyphens() {
		MiniProgramInvitationCodeCodec codec = new MiniProgramInvitationCodeCodec();

		String generated = codec.generate();

		assertThat(generated).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
		assertThat(codec.normalize(" abcd - efgh - jkmn ")).isEqualTo("ABCDEFGHJKMN");
		assertThat(codec.hash("ABCD-EFGH-JKMN")).isEqualTo(codec.hash("abcd efgh jkmn"));
	}

	@Test
	void wechatIdentityHashPreservesCaseAndSeparatorsFromTheTrustedWechatIdentity() {
		MiniProgramInvitationCodeCodec codec = new MiniProgramInvitationCodeCodec();

		assertThat(codec.identityHash("wx-app", "Open-Id"))
			.isNotEqualTo(codec.identityHash("wx-app", "openid"));
		assertThat(codec.identityHash("wx-app", "open id"))
			.isNotEqualTo(codec.identityHash("wx-app", "openid"));
	}

	@Test
	void newWechatIdentityWithoutInvitationIsRejectedBeforeAnyClaimOrReservation() {
		MiniProgramInvitationStore invitations = org.mockito.Mockito.mock(MiniProgramInvitationStore.class);
		MiniProgramInvitationClaimStore claims = org.mockito.Mockito.mock(MiniProgramInvitationClaimStore.class);
		MiniProgramInvitationService service = service(invitations, claims);

		assertThatThrownBy(() -> service.authorizeFirstLogin(null, "wx-app", "openid"))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.getStatus().value()).isEqualTo(403);
				assertThat(exception.getCode()).isEqualTo("INVITATION_REQUIRED");
			});
		verify(claims, never()).claim(any(), any(), any());
		verify(invitations, never()).reserve(any(), any(), any());
	}

	@Test
	void validInvitationReservesExactlyOneUseAndRetryIsAcceptedWithoutAnotherUse() {
		MiniProgramInvitationStore invitations = org.mockito.Mockito.mock(MiniProgramInvitationStore.class);
		MiniProgramInvitationClaimStore claims = org.mockito.Mockito.mock(MiniProgramInvitationClaimStore.class);
		MiniProgramInvitationService service = service(invitations, claims);
		MiniProgramInvitation invitation = invitation("invite-1");
		when(invitations.findByCodeHash(any())).thenReturn(Optional.of(invitation));
		when(claims.claim(any(), org.mockito.ArgumentMatchers.eq("invite-1"), any()))
			.thenReturn(InvitationClaimResult.ACQUIRED, InvitationClaimResult.SAME_INVITATION);
		when(invitations.reserve(org.mockito.ArgumentMatchers.eq("invite-1"), any(), any()))
			.thenReturn(InvitationReservationResult.RESERVED, InvitationReservationResult.ALREADY_RESERVED);

		var first = service.authorizeFirstLogin("ABCD-EFGH-JKMN", "wx-app", "openid");
		var retry = service.authorizeFirstLogin("abcd efgh jkmn", "wx-app", "openid");

		verify(invitations, org.mockito.Mockito.times(2))
			.reserve(org.mockito.ArgumentMatchers.eq("invite-1"), any(), any());
		assertThat(first.invitationId()).isEqualTo("invite-1");
		assertThat(first.invitationName()).isEqualTo("审核体验");
		assertThat(first.invitationCodeSuffix()).isEqualTo("JKMN");
		assertThat(first.invitationRedeemedAt()).isEqualTo(CLOCK.instant());
		assertThat(retry).isEqualTo(first);
	}

	@Test
	void invalidDisabledOrExhaustedInvitationUsesOneGenericPublicError() {
		MiniProgramInvitationStore invitations = org.mockito.Mockito.mock(MiniProgramInvitationStore.class);
		MiniProgramInvitationClaimStore claims = org.mockito.Mockito.mock(MiniProgramInvitationClaimStore.class);
		MiniProgramInvitationService service = service(invitations, claims);
		when(invitations.findByCodeHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.authorizeFirstLogin("ABCD-EFGH-JKMN", "wx-app", "openid"))
			.isInstanceOfSatisfying(ApiException.class, exception -> {
				assertThat(exception.getStatus().value()).isEqualTo(403);
				assertThat(exception.getCode()).isEqualTo("INVITATION_CODE_INVALID");
				assertThat(exception.getMessage()).isEqualTo("邀请码无效或已失效，请联系管理员");
			});
	}

	@Test
	void unavailableReservationReleasesAClaimLeftByAnInterruptedEarlierAttempt() {
		MiniProgramInvitationStore invitations = org.mockito.Mockito.mock(MiniProgramInvitationStore.class);
		MiniProgramInvitationClaimStore claims = org.mockito.Mockito.mock(MiniProgramInvitationClaimStore.class);
		MiniProgramInvitationService service = service(invitations, claims);
		MiniProgramInvitation invitation = invitation("invite-1");
		when(invitations.findByCodeHash(any())).thenReturn(Optional.of(invitation));
		when(claims.claim(any(), org.mockito.ArgumentMatchers.eq("invite-1"), any()))
			.thenReturn(InvitationClaimResult.SAME_INVITATION);
		when(invitations.reserve(org.mockito.ArgumentMatchers.eq("invite-1"), any(), any()))
			.thenReturn(InvitationReservationResult.UNAVAILABLE);

		assertThatThrownBy(() -> service.authorizeFirstLogin("ABCD-EFGH-JKMN", "wx-app", "openid"))
			.isInstanceOfSatisfying(ApiException.class, exception ->
				assertThat(exception.getCode()).isEqualTo("INVITATION_CODE_INVALID")
			);

		verify(claims).release(any(), org.mockito.ArgumentMatchers.eq("invite-1"));
	}

	@Test
	void createReturnsPlainCodeOnceWhileStoredInvitationContainsOnlyHashAndSuffix() {
		MiniProgramInvitationStore invitations = org.mockito.Mockito.mock(MiniProgramInvitationStore.class);
		MiniProgramInvitationClaimStore claims = org.mockito.Mockito.mock(MiniProgramInvitationClaimStore.class);
		MiniProgramInvitationService service = service(invitations, claims);
		when(invitations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var created = service.create("审核体验", "提审使用", 5, admin());

		assertThat(created.invitationCode()).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
		assertThat(created.view().name()).isEqualTo("审核体验");
		assertThat(created.view().maxUses()).isEqualTo(5);
		assertThat(created.view().usedCount()).isZero();
		org.mockito.ArgumentCaptor<MiniProgramInvitation> captor =
			org.mockito.ArgumentCaptor.forClass(MiniProgramInvitation.class);
		verify(invitations).save(captor.capture());
		assertThat(captor.getValue().getCodeHash()).hasSize(64);
		assertThat(captor.getValue().getCodeSuffix()).hasSize(4);
		assertThat(captor.getValue().getClass().getDeclaredFields())
			.extracting(java.lang.reflect.Field::getName)
			.doesNotContain("invitationCode", "rawCode", "code");
	}

	private MiniProgramInvitationService service(
		MiniProgramInvitationStore invitations,
		MiniProgramInvitationClaimStore claims
	) {
		return new MiniProgramInvitationService(
			invitations, claims, new MiniProgramInvitationCodeCodec(), CLOCK
		);
	}

	private MiniProgramInvitation invitation(String id) {
		MiniProgramInvitation value = new MiniProgramInvitation();
		value.setId(id);
		value.setName("审核体验");
		value.setCodeSuffix("JKMN");
		value.setStatus(com.recording.platform.identity.invitation.model.InvitationStatus.ACTIVE);
		value.setMaxUses(5);
		value.setUsedCount(0);
		return value;
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-1", "WEB-0123456789abcdef01234567", "admin", "管理员",
			UserRole.ADMIN, SessionType.WEB, false
		);
	}
}

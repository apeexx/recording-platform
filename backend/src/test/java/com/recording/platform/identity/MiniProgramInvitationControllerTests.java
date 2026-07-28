package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.api.PageResponse;
import com.recording.platform.identity.controller.MiniProgramInvitationController;
import com.recording.platform.identity.dto.CreateMiniProgramInvitationRequest;
import com.recording.platform.identity.invitation.MiniProgramInvitationCreated;
import com.recording.platform.identity.invitation.MiniProgramInvitationService;
import com.recording.platform.identity.invitation.MiniProgramInvitationView;
import com.recording.platform.identity.invitation.model.InvitationEffectiveStatus;
import com.recording.platform.identity.model.SessionType;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.idempotency.IdempotencyService;
import com.recording.platform.security.PlatformPrincipal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class MiniProgramInvitationControllerTests {
	@Test
	void creationResponseContainsPlainCodeButListAndDisableReturnOnlyMaskedView() {
		MiniProgramInvitationService service = org.mockito.Mockito.mock(MiniProgramInvitationService.class);
		IdempotencyService idempotency = org.mockito.Mockito.mock(IdempotencyService.class);
		MiniProgramInvitationController controller = new MiniProgramInvitationController(service, idempotency);
		PlatformPrincipal principal = admin();
		MiniProgramInvitationView view = view();
		when(service.create("审核体验", "提审使用", 5, principal))
			.thenReturn(new MiniProgramInvitationCreated(view, "ABCD-EFGH-JKMN"));
		when(service.list(0, 20)).thenReturn(new PageResponse<>(List.of(view), 0, 20, 1));

		var created = controller.create(
			new CreateMiniProgramInvitationRequest("审核体验", "提审使用", 5),
			principal
		);
		var listed = controller.list(0, 20);

		assertThat(created.invitationCode()).isEqualTo("ABCD-EFGH-JKMN");
		assertThat(created.codeSuffix()).isEqualTo("JKMN");
		assertThat(listed.items()).singleElement().satisfies(item ->
			assertThat(item.codeSuffix()).isEqualTo("JKMN")
		);
		assertThat(MiniProgramInvitationView.class.getDeclaredFields())
			.extracting(java.lang.reflect.Field::getName)
			.doesNotContain("invitationCode");
	}

	@Test
	void disableUsesSharedIdempotencyWithoutPersistingAnyPlainInvitationCode() {
		MiniProgramInvitationService service = org.mockito.Mockito.mock(MiniProgramInvitationService.class);
		IdempotencyService idempotency = org.mockito.Mockito.mock(IdempotencyService.class);
		MiniProgramInvitationController controller = new MiniProgramInvitationController(service, idempotency);
		TestingAuthenticationToken authentication =
			new TestingAuthenticationToken(admin(), null, "ROLE_ADMIN");
		when(idempotency.execute(
			authentication,
			"miniprogram-invitation:disable:invite-1",
			"disable-operation",
			MiniProgramInvitationView.class,
			() -> service.disable("invite-1", admin())
		)).thenReturn(view());

		controller.disable("invite-1", "disable-operation", authentication, admin());

		verify(idempotency).execute(
			org.mockito.ArgumentMatchers.eq(authentication),
			org.mockito.ArgumentMatchers.eq("miniprogram-invitation:disable:invite-1"),
			org.mockito.ArgumentMatchers.eq("disable-operation"),
			org.mockito.ArgumentMatchers.eq(MiniProgramInvitationView.class),
			org.mockito.ArgumentMatchers.any()
		);
	}

	private PlatformPrincipal admin() {
		return new PlatformPrincipal(
			"session-1", "WEB-0123456789abcdef01234567", "admin", "管理员",
			UserRole.ADMIN, SessionType.WEB, false
		);
	}

	private MiniProgramInvitationView view() {
		return new MiniProgramInvitationView(
			"invite-1", "审核体验", "提审使用", "JKMN", 5, 0, 5,
			InvitationEffectiveStatus.ACTIVE,
			"WEB-0123456789abcdef01234567", "管理员",
			Instant.parse("2026-07-28T08:00:00Z"),
			null, null, null
		);
	}
}

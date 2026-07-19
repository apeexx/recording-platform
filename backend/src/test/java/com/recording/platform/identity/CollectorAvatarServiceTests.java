package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.CollectorAvatarService;
import com.recording.platform.identity.store.UserStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class CollectorAvatarServiceTests {
	@TempDir Path root;
	private final UserStore users = mock(UserStore.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void uploadsMagicCheckedPngAndPersistsOnlyRelativePath() throws Exception {
		UserAccount user = collector();
		when(users.findById("collector-1")).thenReturn(Optional.of(user));
		when(users.updateCollectorAvatarIfActive(eq("collector-1"), eq("collector-1.png"), eq("image/png"), any()))
			.thenAnswer(invocation -> { user.setAvatarPath(invocation.getArgument(1)); user.setAvatarContentType(invocation.getArgument(2)); return Optional.of(user); });
		byte[] png = new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3};
		CollectorAvatarService service = new CollectorAvatarService(users, root, clock);

		UserAccount saved = service.upload("collector-1", new MockMultipartFile("avatar", "me.png", "image/png", png));

		assertThat(saved.getAvatarPath()).isEqualTo("collector-1.png");
		assertThat(Files.readAllBytes(root.resolve("collector-1.png"))).isEqualTo(png);
	}

	@Test
	void rejectsExtensionOrMagicMismatch() {
		when(users.findById("collector-1")).thenReturn(Optional.of(collector()));
		CollectorAvatarService service = new CollectorAvatarService(users, root, clock);

		assertThatThrownBy(() -> service.upload("collector-1", new MockMultipartFile(
			"avatar", "fake.png", "image/png", new byte[]{1,2,3,4,5,6,7,8}
		))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_AVATAR_FILE");
	}

	private UserAccount collector() {
		UserAccount user = new UserAccount();
		user.setId("collector-1"); user.setRole(UserRole.COLLECTOR); user.setStatus(UserStatus.ACTIVE);
		return user;
	}
}

package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.service.CollectorAvatarService;
import com.recording.platform.identity.store.MiniProgramUserStore;
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
	private final MiniProgramUserStore users = mock(MiniProgramUserStore.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void uploadsMagicCheckedPngAndPersistsOnlyRelativePath() throws Exception {
		MiniProgramUser user = collector();
		when(users.findById("MINI-0123456789abcdef01234567")).thenReturn(Optional.of(user));
		when(users.updateAvatarIfActive(eq("MINI-0123456789abcdef01234567"), eq("MINI-0123456789abcdef01234567.png"), eq("image/png"), any()))
			.thenAnswer(invocation -> { user.setAvatarPath(invocation.getArgument(1)); user.setAvatarContentType(invocation.getArgument(2)); return Optional.of(user); });
		byte[] png = new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3};
		CollectorAvatarService service = new CollectorAvatarService(users, root, clock);

		MiniProgramUser saved = service.upload(user.getId(), new MockMultipartFile("avatar", "me.png", "image/png", png));

		assertThat(saved.getAvatarPath()).isEqualTo(user.getId()+".png");
		assertThat(Files.readAllBytes(root.resolve(user.getId()+".png"))).isEqualTo(png);
	}

	@Test
	void rejectsExtensionOrMagicMismatch() {
		when(users.findById("MINI-0123456789abcdef01234567")).thenReturn(Optional.of(collector()));
		CollectorAvatarService service = new CollectorAvatarService(users, root, clock);

		assertThatThrownBy(() -> service.upload("MINI-0123456789abcdef01234567", new MockMultipartFile(
			"avatar", "fake.png", "image/png", new byte[]{1,2,3,4,5,6,7,8}
		))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_AVATAR_FILE");
	}

	@Test void readsAndDeletesOnlyTheStoredRelativeAvatar() throws Exception {MiniProgramUser user=collector();user.setAvatarPath(user.getId()+".png");user.setAvatarContentType("image/png");byte[] png=new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};Files.write(root.resolve(user.getAvatarPath()),png);
		MiniProgramUser cleared=collector();when(users.findById(user.getId())).thenReturn(Optional.of(user));when(users.clearAvatarIfActive(eq(user.getId()),any())).thenReturn(Optional.of(cleared));CollectorAvatarService service=new CollectorAvatarService(users,root,clock);
		assertThat(Files.readAllBytes(service.read(user.getId()).path())).isEqualTo(png);service.delete(user.getId());assertThat(root.resolve(user.getId()+".png")).doesNotExist();}

	private MiniProgramUser collector() {
		MiniProgramUser user = new MiniProgramUser();
		user.setId("MINI-0123456789abcdef01234567"); user.setStatus(UserStatus.ACTIVE);
		return user;
	}
}

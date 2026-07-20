package com.recording.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import com.recording.platform.identity.store.mongo.MongoIdentityDirectory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityDirectoryTests {
	@Test
	void routesReadsByPrefixAndInterpretsMiniProgramUsersAsCollectors() {
		WebUserStore webUsers = mock(WebUserStore.class);
		MiniProgramUserStore miniUsers = mock(MiniProgramUserStore.class);
		WebUser web = new WebUser();
		web.setId("WEB-0123456789abcdef01234567");
		web.setUsername("admin");
		web.setRole(UserRole.ADMIN);
		web.setStatus(UserStatus.ACTIVE);
		MiniProgramUser mini = new MiniProgramUser();
		mini.setId("MINI-0123456789abcdef01234567");
		mini.setAccount("682913");
		mini.setStatus(UserStatus.ACTIVE);
		when(webUsers.findById(web.getId())).thenReturn(Optional.of(web));
		when(miniUsers.findById(mini.getId())).thenReturn(Optional.of(mini));
		MongoIdentityDirectory directory = new MongoIdentityDirectory(webUsers, miniUsers);

		assertThat(directory.findById(web.getId()).orElseThrow().loginName()).isEqualTo("admin");
		assertThat(directory.findById(mini.getId()).orElseThrow().role()).isEqualTo(UserRole.COLLECTOR);
		assertThat(directory.findById("legacy-id")).isEmpty();
		verify(webUsers, never()).findById(mini.getId());
		verify(miniUsers, never()).findById(web.getId());
	}

	@Test
	void batchesCrossCollectionReadsWithoutProvidingAnyWriteApi() {
		WebUserStore webUsers = mock(WebUserStore.class);
		MiniProgramUserStore miniUsers = mock(MiniProgramUserStore.class);
		WebUser web = new WebUser(); web.setId("WEB-0123456789abcdef01234567");
		MiniProgramUser mini = new MiniProgramUser(); mini.setId("MINI-0123456789abcdef01234567");
		when(webUsers.findAllByIdIn(List.of(web.getId()))).thenReturn(List.of(web));
		when(miniUsers.findAllByIdIn(List.of(mini.getId()))).thenReturn(List.of(mini));
		MongoIdentityDirectory directory = new MongoIdentityDirectory(webUsers, miniUsers);

		assertThat(directory.findAllByIdIn(List.of(web.getId(), mini.getId(), "unknown")))
			.extracting(identity -> identity.id())
			.containsExactlyInAnyOrder(web.getId(), mini.getId());
		assertThat(java.util.Arrays.stream(directory.getClass().getMethods()).map(method -> method.getName()))
			.doesNotContain("save", "update", "delete");
	}
}

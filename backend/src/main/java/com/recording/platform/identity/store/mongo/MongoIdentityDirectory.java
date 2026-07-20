package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.IdentityUser;
import com.recording.platform.identity.store.IdentityDirectory;
import com.recording.platform.identity.store.MiniProgramUserStore;
import com.recording.platform.identity.store.WebUserStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MongoIdentityDirectory implements IdentityDirectory {
	private final WebUserStore webUsers;
	private final MiniProgramUserStore miniProgramUsers;

	public MongoIdentityDirectory(WebUserStore webUsers, MiniProgramUserStore miniProgramUsers) {
		this.webUsers = webUsers;
		this.miniProgramUsers = miniProgramUsers;
	}

	@Override
	public Optional<IdentityUser> findById(String id) {
		if (id == null) return Optional.empty();
		if (id.startsWith("WEB-")) return webUsers.findById(id).map(IdentityUser::from);
		if (id.startsWith("MINI-")) return miniProgramUsers.findById(id).map(IdentityUser::from);
		return Optional.empty();
	}

	@Override
	public List<IdentityUser> findAllByIdIn(Collection<String> ids) {
		List<String> webIds = ids == null ? List.of() : ids.stream().filter(id -> id != null && id.startsWith("WEB-")).distinct().toList();
		List<String> miniIds = ids == null ? List.of() : ids.stream().filter(id -> id != null && id.startsWith("MINI-")).distinct().toList();
		List<IdentityUser> result = new ArrayList<>();
		result.addAll(webUsers.findAllByIdIn(webIds).stream().map(IdentityUser::from).toList());
		result.addAll(miniProgramUsers.findAllByIdIn(miniIds).stream().map(IdentityUser::from).toList());
		return List.copyOf(result);
	}
}

package com.recording.platform.identity.store;

import com.recording.platform.identity.model.IdentityUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IdentityDirectory {
	Optional<IdentityUser> findById(String id);
	List<IdentityUser> findAllByIdIn(Collection<String> ids);
}

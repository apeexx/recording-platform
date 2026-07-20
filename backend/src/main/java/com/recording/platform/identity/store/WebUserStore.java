package com.recording.platform.identity.store;

import com.recording.platform.identity.model.WebUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.recording.platform.identity.model.UserRole;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WebUserStore {
	WebUser save(WebUser user);
	Optional<WebUser> findById(String id);
	List<WebUser> findAllByIdIn(Collection<String> ids);
	Optional<WebUser> findByUsername(String username);
	boolean existsByRole(UserRole role);
	Page<WebUser> findAll(Pageable pageable);
	Page<WebUser> search(String query, UserRole role, Pageable pageable);
	Optional<WebUser> disableIfActive(String userId, Instant updatedAt);
	boolean updatePasswordIfActive(String userId, String expectedPasswordHash, String passwordHash, Instant updatedAt);
	Optional<WebUser> resetPasswordIfActive(String userId, String passwordHash, Instant updatedAt);
}

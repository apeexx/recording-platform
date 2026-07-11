package com.recording.platform.identity.store;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

public interface UserStore {
	UserAccount save(UserAccount user);

	Optional<UserAccount> findById(String id);

	Optional<UserAccount> findByUsername(String username);

	Optional<UserAccount> findByWechatIdentity(String appId, String openId);

	boolean existsByRole(UserRole role);

	Page<UserAccount> findAll(Pageable pageable);

	Page<UserAccount> findAllBackend(Pageable pageable);

	Optional<UserAccount> disableBackendIfActive(String userId, Instant updatedAt);

	boolean updatePasswordIfActive(String userId, String expectedPasswordHash, String passwordHash, Instant updatedAt);

	Optional<UserAccount> updateCollectorNameIfActive(String userId, String name, Instant updatedAt);
}

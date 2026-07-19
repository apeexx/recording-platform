package com.recording.platform.identity.store;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface UserStore {
	UserAccount save(UserAccount user);

	Optional<UserAccount> findById(String id);
	default List<UserAccount> findAllByIdIn(Collection<String> ids) { return List.of(); }

	Optional<UserAccount> findByUsername(String username);

	Optional<UserAccount> findByWechatIdentity(String appId, String openId);

	boolean existsByRole(UserRole role);

	Page<UserAccount> findAll(Pageable pageable);

	Page<UserAccount> findAllBackend(Pageable pageable);

	default Page<UserAccount> search(String query, UserRole role, Pageable pageable) {
		return role == null ? findAll(pageable) : Page.empty(pageable);
	}

	Optional<UserAccount> disableBackendIfActive(String userId, Instant updatedAt);

	boolean updatePasswordIfActive(String userId, String expectedPasswordHash, String passwordHash, Instant updatedAt);

	default Optional<UserAccount> resetBackendPasswordIfActive(String userId, String passwordHash, Instant updatedAt) {
		return Optional.empty();
	}

	Optional<UserAccount> updateCollectorNameIfActive(String userId, String name, Instant updatedAt);

	default Optional<UserAccount> completeCollectorProfileIfActive(
		String userId,
		String username,
		String name,
		String passwordHash,
		Instant updatedAt
	) { return Optional.empty(); }

	default Optional<UserAccount> updateCollectorPasswordIfActive(
		String userId,
		String expectedPasswordHash,
		String passwordHash,
		Instant updatedAt
	) { return Optional.empty(); }

	default Optional<UserAccount> updateCollectorAvatarIfActive(
		String userId, String avatarPath, String contentType, Instant updatedAt
	) { return Optional.empty(); }

	default Optional<UserAccount> clearCollectorAvatarIfActive(String userId, Instant updatedAt) {
		return Optional.empty();
	}

	default Optional<UserAccount> updateCollectorAccountIfActive(String userId, String account, Instant updatedAt) {
		return Optional.empty();
	}

	default Optional<UserAccount> resetCollectorPasswordIfActive(String userId, String passwordHash, Instant updatedAt) {
		return Optional.empty();
	}
}

package com.recording.platform.identity.store;

import com.recording.platform.identity.model.MiniProgramUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MiniProgramUserStore {
	MiniProgramUser save(MiniProgramUser user);
	Optional<MiniProgramUser> findById(String id);
	List<MiniProgramUser> findAllByIdIn(Collection<String> ids);
	Optional<MiniProgramUser> findByAccount(String account);
	Optional<MiniProgramUser> findByWechatIdentity(String appId, String openId);
	Page<MiniProgramUser> search(String query, Pageable pageable);
	Optional<MiniProgramUser> updateNameIfActive(String userId, String name, Instant updatedAt);
	Optional<MiniProgramUser> completeProfileIfActive(String userId, String account, String name, String passwordHash, Instant updatedAt);
	Optional<MiniProgramUser> updatePasswordIfActive(String userId, String expectedPasswordHash, String passwordHash, Instant updatedAt);
	Optional<MiniProgramUser> updateAvatarIfActive(String userId, String avatarPath, String contentType, Instant updatedAt);
	Optional<MiniProgramUser> clearAvatarIfActive(String userId, Instant updatedAt);
	Optional<MiniProgramUser> updateAccountIfActive(String userId, String account, Instant updatedAt);
	Optional<MiniProgramUser> resetPasswordIfActive(String userId, String passwordHash, Instant updatedAt);
}

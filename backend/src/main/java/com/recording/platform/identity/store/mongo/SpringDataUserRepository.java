package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpringDataUserRepository extends MongoRepository<UserAccount, String> {
	Optional<UserAccount> findByUsername(String username);

	Optional<UserAccount> findByWechatAppIdAndWechatOpenId(String wechatAppId, String wechatOpenId);

	boolean existsByRole(UserRole role);

	Page<UserAccount> findAllByRoleIn(Collection<UserRole> roles, Pageable pageable);
}

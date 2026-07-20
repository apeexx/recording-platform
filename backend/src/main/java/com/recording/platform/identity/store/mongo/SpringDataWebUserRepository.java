package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.WebUser;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataWebUserRepository extends MongoRepository<WebUser, String> {
	Optional<WebUser> findByUsername(String username);
	boolean existsByRole(UserRole role);
}

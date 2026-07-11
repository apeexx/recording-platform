package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.store.UserStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import com.recording.platform.identity.model.UserStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Repository
public class MongoUserStore implements UserStore {
	private final SpringDataUserRepository repository;
	private final MongoTemplate mongoTemplate;

	public MongoUserStore(SpringDataUserRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public UserAccount save(UserAccount user) {
		return repository.save(user);
	}

	@Override
	public Optional<UserAccount> findById(String id) {
		return repository.findById(id);
	}

	@Override
	public Optional<UserAccount> findByUsername(String username) {
		return repository.findByUsername(username);
	}

	@Override
	public Optional<UserAccount> findByWechatIdentity(String appId, String openId) {
		return repository.findByWechatAppIdAndWechatOpenId(appId, openId);
	}

	@Override
	public boolean existsByRole(UserRole role) {
		return repository.existsByRole(role);
	}

	@Override
	public Page<UserAccount> findAll(Pageable pageable) {
		return repository.findAll(pageable);
	}

	@Override
	public Page<UserAccount> findAllBackend(Pageable pageable) {
		return repository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.REVIEWER), pageable);
	}

	@Override
	public Optional<UserAccount> disableBackendIfActive(String userId, Instant updatedAt) {
		Query query = Query.query(Criteria.where("_id").is(userId)
			.and("role").in(UserRole.ADMIN, UserRole.REVIEWER)
			.and("status").is(UserStatus.ACTIVE));
		Update update = new Update()
			.set("status", UserStatus.DISABLED)
			.set("updatedAt", updatedAt);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			UserAccount.class
		));
	}

	@Override
	public boolean updatePasswordIfActive(
		String userId,
		String expectedPasswordHash,
		String passwordHash,
		Instant updatedAt
	) {
		Query query = Query.query(Criteria.where("_id").is(userId)
			.and("status").is(UserStatus.ACTIVE)
			.and("passwordHash").is(expectedPasswordHash));
		Update update = new Update()
			.set("passwordHash", passwordHash)
			.set("firstPasswordChangeRequired", false)
			.set("updatedAt", updatedAt);
		return mongoTemplate.updateFirst(query, update, UserAccount.class).getMatchedCount() == 1;
	}

	@Override
	public Optional<UserAccount> updateCollectorNameIfActive(String userId, String name, Instant updatedAt) {
		Query query = Query.query(Criteria.where("_id").is(userId)
			.and("role").is(UserRole.COLLECTOR)
			.and("status").is(UserStatus.ACTIVE));
		Update update = new Update().set("name", name).set("updatedAt", updatedAt);
		return Optional.ofNullable(mongoTemplate.findAndModify(
			query,
			update,
			FindAndModifyOptions.options().returnNew(true),
			UserAccount.class
		));
	}
}

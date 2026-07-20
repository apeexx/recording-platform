package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.model.WebUser;
import com.recording.platform.identity.store.WebUserStore;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoWebUserStore implements WebUserStore {
	private final SpringDataWebUserRepository repository;
	private final MongoTemplate mongo;

	public MongoWebUserStore(SpringDataWebUserRepository repository, MongoTemplate mongo) {
		this.repository = repository; this.mongo = mongo;
	}
	@Override public WebUser save(WebUser user) { return repository.save(user); }
	@Override public Optional<WebUser> findById(String id) { return repository.findById(id); }
	@Override public List<WebUser> findAllByIdIn(Collection<String> ids) { return repository.findAllById(ids); }
	@Override public Optional<WebUser> findByUsername(String username) { return repository.findByUsername(username); }
	@Override public boolean existsByRole(UserRole role) { return repository.existsByRole(role); }
	@Override public Page<WebUser> findAll(Pageable pageable) { return repository.findAll(pageable); }
	@Override public Page<WebUser> search(String term, UserRole role, Pageable pageable) {
		List<Criteria> filters = new java.util.ArrayList<>();
		if (role != null) filters.add(Criteria.where("role").is(role));
		if (term != null && !term.isBlank()) {
			String safe = Pattern.quote(term.trim());
			filters.add(new Criteria().orOperator(
				Criteria.where("name").regex(safe, "i"),
				Criteria.where("username").regex(safe, "i"),
				Criteria.where("_id").is(term.trim())
			));
		}
		Query query = new Query(filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters));
		long total = mongo.count(Query.of(query).limit(-1).skip(-1), WebUser.class); query.with(pageable);
		return new PageImpl<>(mongo.find(query, WebUser.class), pageable, total);
	}
	@Override public Optional<WebUser> disableIfActive(String userId, Instant updatedAt) {
		Query query = Query.query(Criteria.where("_id").is(userId).and("status").is(UserStatus.ACTIVE));
		Update update = new Update().set("status", UserStatus.DISABLED).set("updatedAt", updatedAt);
		return Optional.ofNullable(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), WebUser.class));
	}
	@Override public boolean updatePasswordIfActive(String userId, String expected, String passwordHash, Instant updatedAt) {
		Query query = Query.query(Criteria.where("_id").is(userId).and("status").is(UserStatus.ACTIVE).and("passwordHash").is(expected));
		Update update = new Update().set("passwordHash", passwordHash).set("firstPasswordChangeRequired", false).set("updatedAt", updatedAt);
		return mongo.updateFirst(query, update, WebUser.class).getMatchedCount() == 1;
	}
	@Override public Optional<WebUser> resetPasswordIfActive(String userId, String passwordHash, Instant updatedAt) {
		Query query = Query.query(Criteria.where("_id").is(userId).and("status").is(UserStatus.ACTIVE));
		Update update = new Update().set("passwordHash", passwordHash).set("firstPasswordChangeRequired", true).set("updatedAt", updatedAt);
		return Optional.ofNullable(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), WebUser.class));
	}
}

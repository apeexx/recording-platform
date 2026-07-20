package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.MiniProgramUser;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.MiniProgramUserStore;
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
public class MongoMiniProgramUserStore implements MiniProgramUserStore {
	private final SpringDataMiniProgramUserRepository repository;
	private final MongoTemplate mongo;
	public MongoMiniProgramUserStore(SpringDataMiniProgramUserRepository repository, MongoTemplate mongo) { this.repository=repository; this.mongo=mongo; }
	@Override public MiniProgramUser save(MiniProgramUser user) { return repository.save(user); }
	@Override public Optional<MiniProgramUser> findById(String id) { return repository.findById(id); }
	@Override public List<MiniProgramUser> findAllByIdIn(Collection<String> ids) { return repository.findAllById(ids); }
	@Override public Optional<MiniProgramUser> findByAccount(String account) { return repository.findByAccount(account); }
	@Override public Optional<MiniProgramUser> findByWechatIdentity(String appId, String openId) { return repository.findByWechatAppIdAndWechatOpenId(appId, openId); }
	@Override public Page<MiniProgramUser> search(String term, Pageable pageable) {
		Query query = new Query();
		if (term != null && !term.isBlank()) {
			String safe = Pattern.quote(term.trim());
			query.addCriteria(new Criteria().orOperator(
				Criteria.where("name").regex(safe, "i"),
				Criteria.where("account").regex(safe, "i"),
				Criteria.where("_id").is(term.trim())
			));
		}
		long total = mongo.count(Query.of(query).limit(-1).skip(-1), MiniProgramUser.class); query.with(pageable);
		return new PageImpl<>(mongo.find(query, MiniProgramUser.class), pageable, total);
	}
	private Query active(String id) { return Query.query(Criteria.where("_id").is(id).and("status").is(UserStatus.ACTIVE)); }
	private Optional<MiniProgramUser> modify(Query query, Update update) { return Optional.ofNullable(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), MiniProgramUser.class)); }
	@Override public Optional<MiniProgramUser> updateNameIfActive(String id, String name, Instant at) { return modify(active(id), new Update().set("name",name).set("updatedAt",at)); }
	@Override public Optional<MiniProgramUser> completeProfileIfActive(String id, String account, String name, String hash, Instant at) {
		Query query=active(id); query.addCriteria(Criteria.where("account").is(null).and("passwordHash").is(null));
		return modify(query,new Update().set("account",account).set("name",name).set("passwordHash",hash).set("updatedAt",at));
	}
	@Override public Optional<MiniProgramUser> updatePasswordIfActive(String id,String expected,String hash,Instant at) { Query q=active(id);q.addCriteria(Criteria.where("passwordHash").is(expected));return modify(q,new Update().set("passwordHash",hash).set("updatedAt",at)); }
	@Override public Optional<MiniProgramUser> updateAvatarIfActive(String id,String path,String type,Instant at) { return modify(active(id),new Update().set("avatarPath",path).set("avatarContentType",type).set("avatarUpdatedAt",at).set("updatedAt",at)); }
	@Override public Optional<MiniProgramUser> clearAvatarIfActive(String id,Instant at) { return modify(active(id),new Update().unset("avatarPath").unset("avatarContentType").unset("avatarUpdatedAt").set("updatedAt",at)); }
	@Override public Optional<MiniProgramUser> updateAccountIfActive(String id,String account,Instant at) { return modify(active(id),new Update().set("account",account).set("updatedAt",at)); }
	@Override public Optional<MiniProgramUser> resetPasswordIfActive(String id,String hash,Instant at) { return modify(active(id),new Update().set("passwordHash",hash).set("updatedAt",at)); }
}

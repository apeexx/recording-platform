package com.recording.platform.identity.store.mongo;

import com.recording.platform.identity.model.MiniProgramUser;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpringDataMiniProgramUserRepository extends MongoRepository<MiniProgramUser, String> {
	Optional<MiniProgramUser> findByAccount(String account);
	Optional<MiniProgramUser> findByWechatAppIdAndWechatOpenId(String wechatAppId, String wechatOpenId);
}

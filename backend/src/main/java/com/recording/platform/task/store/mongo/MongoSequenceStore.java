package com.recording.platform.task.store.mongo;

import com.recording.platform.task.model.SequenceRecord;
import com.recording.platform.task.store.SequenceStore;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoSequenceStore implements SequenceStore {
	private final MongoTemplate mongoTemplate;

	public MongoSequenceStore(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public long next(String key) {
		SequenceRecord sequence = mongoTemplate.findAndModify(
			Query.query(Criteria.where("_id").is(key)),
			new Update().inc("value", 1),
			FindAndModifyOptions.options().upsert(true).returnNew(true),
			SequenceRecord.class
		);
		if (sequence == null || sequence.getValue() < 1) {
			throw new IllegalStateException("Unable to allocate sequence: " + key);
		}
		return sequence.getValue();
	}
}

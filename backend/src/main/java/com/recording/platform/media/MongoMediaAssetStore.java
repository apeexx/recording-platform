package com.recording.platform.media;

import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MongoMediaAssetStore implements MediaAssetStore {
	private final SpringDataMediaAssetRepository repository;

	public MongoMediaAssetStore(SpringDataMediaAssetRepository repository) {
		this.repository = repository;
	}

	@Override public MediaAsset save(MediaAsset asset) { return repository.save(asset); }
	@Override public Optional<MediaAsset> findById(String id) { return repository.findById(id); }
	@Override public void deleteById(String id) { repository.deleteById(id); }
}

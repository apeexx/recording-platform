package com.recording.platform.media;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMediaAssetRepository extends MongoRepository<MediaAsset, String> {
}

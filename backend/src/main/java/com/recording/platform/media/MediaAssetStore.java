package com.recording.platform.media;

import java.util.Optional;

public interface MediaAssetStore {
	MediaAsset save(MediaAsset asset);
	Optional<MediaAsset> findById(String id);
	void deleteById(String id);
}

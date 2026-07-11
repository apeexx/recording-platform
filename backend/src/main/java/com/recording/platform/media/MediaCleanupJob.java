package com.recording.platform.media;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "media_cleanup_jobs")
@CompoundIndexes({
	@CompoundIndex(
		name = "unique_item_cleanup_operation",
		def = "{'itemId': 1, 'operationId': 1}",
		unique = true
	),
	@CompoundIndex(name = "media_cleanup_recovery", def = "{'status': 1, 'createdAt': 1}")
})
public class MediaCleanupJob {
	@Id
	private String id;
	@Version
	private Long version;
	private String itemId;
	private String operationId;
	private List<String> relativePaths = new ArrayList<>();
	private List<String> mediaAssetIds = new ArrayList<>();
	private MediaCleanupStatus status;
	private int attempt;
	private String lastErrorSummary;
	private Instant createdAt;
	private Instant updatedAt;
	private Instant completedAt;
}

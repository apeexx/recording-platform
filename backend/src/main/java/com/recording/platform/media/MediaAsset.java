package com.recording.platform.media;

import com.recording.platform.task.model.RecordingFormat;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "media_assets")
@CompoundIndexes({
	@CompoundIndex(name = "media_task_item_kind", def = "{'taskId': 1, 'itemId': 1, 'kind': 1}")
})
public class MediaAsset {
	@Id
	private String id;
	@Version
	private Long version;
	@Indexed
	private String taskId;
	@Indexed
	private String itemId;
	private MediaKind kind;
	@Indexed
	private String relativePath;
	private String contentType;
	private long sizeBytes;
	private RecordingFormat audioFormat;
	private Integer sampleRate;
	private Integer channels;
	private Long durationMillis;
	private String sourceHostname;
	private Integer sourceStatus;
	private String sourceErrorSummary;
	private Instant createdAt;
}

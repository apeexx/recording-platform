package com.recording.platform.review.ai;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "review_ai_configs")
public class ReviewAiConfig {
	@Id
	private String taskId;
	private ReviewAiStageConfig audio;
	private ReviewAiStageConfig text;
	private String updatedBy;
	private Instant updatedAt;
}

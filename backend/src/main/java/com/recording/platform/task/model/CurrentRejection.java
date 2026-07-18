package com.recording.platform.task.model;

import java.time.Instant;
import java.util.List;

public record CurrentRejection(
	List<String> reasons,
	String note,
	Instant rejectedAt,
	String reviewerId,
	String reviewerName
) { }

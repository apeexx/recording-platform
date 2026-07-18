package com.recording.platform.task.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "sequences")
public class SequenceRecord {
	@Id
	private String key;
	private long value;
}

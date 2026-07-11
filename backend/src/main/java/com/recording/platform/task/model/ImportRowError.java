package com.recording.platform.task.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportRowError {
	private long rowNumber;
	private String code;
	private String message;
}

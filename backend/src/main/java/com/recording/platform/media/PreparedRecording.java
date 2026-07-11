package com.recording.platform.media;

import com.recording.platform.task.model.SubmittedRecording;
import java.nio.file.Path;

public record PreparedRecording(
	SubmittedRecording recording,
	Path temporaryPath
) {
}

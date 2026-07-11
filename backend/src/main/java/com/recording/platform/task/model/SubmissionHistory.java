package com.recording.platform.task.model;

import com.recording.platform.task.store.SubmitMutation;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionHistory {
	private String operationId;
	private String assignmentId;
	private Instant submittedAt;
	private Long durationMillis;
	private boolean textPresent;
	private boolean audioPresent;
	private RecordingFormat audioFormat;
	private String reviewConclusion;

	public static SubmissionHistory from(SubmitMutation mutation) {
		SubmissionHistory history = new SubmissionHistory();
		history.setOperationId(mutation.operationId());
		history.setAssignmentId(mutation.assignmentId());
		history.setSubmittedAt(mutation.occurredAt());
		TaskItemResult result = mutation.result();
		SubmittedRecording audio = result == null ? null : result.audio();
		history.setDurationMillis(audio == null ? null : audio.durationMillis());
		history.setAudioPresent(audio != null);
		history.setAudioFormat(audio == null ? null : audio.format());
		history.setTextPresent(result != null && result.text() != null && !result.text().isBlank());
		return history;
	}
}

package com.recording.platform.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.recording.platform.task.model.TaskItem;
import com.recording.platform.task.model.TaskItemResult;
import com.recording.platform.task.model.TaskItemStatus;
import com.recording.platform.task.service.AdminTaskItemGroup;
import com.recording.platform.task.service.TaskItemFilter;
import com.recording.platform.task.service.TaskItemResultKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskItemFilterTests {
	@Test
	void groupsStatusesLikeTheMiniProgramWorkTabs() {
		assertThat(AdminTaskItemGroup.PENDING.statuses())
			.containsExactlyInAnyOrder(TaskItemStatus.RECORDING_PENDING, TaskItemStatus.REWORK_PENDING);
		assertThat(AdminTaskItemGroup.SUBMITTED.statuses()).containsExactly(TaskItemStatus.SUBMITTED);
		assertThat(AdminTaskItemGroup.FINISHED.statuses())
			.containsExactlyInAnyOrder(TaskItemStatus.REVIEW_PENDING, TaskItemStatus.COMPLETED);
		assertThat(AdminTaskItemGroup.DISCARDED.statuses()).containsExactly(TaskItemStatus.DISCARDED);
		assertThat(AdminTaskItemGroup.ALL.statuses()).isEmpty();
	}

	@Test
	void resultKindsDistinguishNoneTextAudioAndCombinedResults() {
		assertThat(TaskItemResultKind.NONE.matches(item(null, null))).isTrue();
		assertThat(TaskItemResultKind.TEXT_ONLY.matches(item("文本", null))).isTrue();
		assertThat(TaskItemResultKind.AUDIO_ONLY.matches(item(null, "media-1"))).isTrue();
		assertThat(TaskItemResultKind.TEXT_AND_AUDIO.matches(item("文本", "media-1"))).isTrue();
		assertThat(TaskItemResultKind.ALL.matches(item("文本", "media-1"))).isTrue();
	}

	@Test
	void collectorFilterSupportsMultipleCollectorsAndUnassignedRows() {
		TaskItemFilter collectors = new TaskItemFilter(
			AdminTaskItemGroup.ALL, Set.of("collector-1", "collector-2"), false, TaskItemResultKind.ALL
		);
		TaskItemFilter withUnassigned = new TaskItemFilter(
			AdminTaskItemGroup.ALL, Set.of("collector-1"), true, TaskItemResultKind.ALL
		);

		assertThat(collectors.matchesCollector(itemWithCollector("collector-2"))).isTrue();
		assertThat(collectors.matchesCollector(itemWithCollector(null))).isFalse();
		assertThat(withUnassigned.matchesCollector(itemWithCollector(null))).isTrue();
	}

	private TaskItem item(String text, String mediaId) {
		TaskItem item = new TaskItem();
		item.setCurrentResult(text == null && mediaId == null ? null :
			new TaskItemResult(mediaId == null ? null :
				new com.recording.platform.task.model.SubmittedRecording(
					mediaId, "recording.wav", com.recording.platform.task.model.RecordingFormat.WAV,
					1, 16000, 1, 1_000
				), text));
		return item;
	}

	private TaskItem itemWithCollector(String collectorId) {
		TaskItem item = new TaskItem();
		item.setCollectorId(collectorId);
		return item;
	}
}

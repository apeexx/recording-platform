package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.ImportJob;
import com.recording.platform.task.model.ImportJobStatus;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoImportJobStoreTests {
	@Test
	void heartbeatProgressAndFinishAreFencedByLeaseOwnerAndReturnTheLatestDocument() {
		SpringDataImportJobRepository repository = org.mockito.Mockito.mock(SpringDataImportJobRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		ImportJob latest = new ImportJob();
		latest.setId("job-1");
		latest.setLeaseOwner("worker-1");
		when(template.findAndModify(
			any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ImportJob.class)
		)).thenReturn(latest);
		MongoImportJobStore store = new MongoImportJobStore(repository, template);
		Instant now = Instant.parse("2026-07-11T12:00:00Z");
		ImportJob state = new ImportJob();
		state.setId("job-1");
		state.setStatus(ImportJobStatus.COMPLETED);
		state.setTotalRows(3);
		state.setSuccessRows(3);
		state.setFailureRows(0);
		state.setRetryRowNumbers(List.of());
		state.setCompletedAt(now);
		state.setUpdatedAt(now);

		assertThat(store.heartbeat("job-1", "worker-1", now, now.plusSeconds(600))).containsSame(latest);
		assertThat(store.saveProgress(state, "worker-1")).containsSame(latest);
		assertThat(store.finish(state, "worker-1")).containsSame(latest);

		ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
		org.mockito.Mockito.verify(template, times(3)).findAndModify(
			queries.capture(), updates.capture(), any(FindAndModifyOptions.class), eq(ImportJob.class)
		);
		assertThat(queries.getAllValues()).allSatisfy((query) -> assertThat(query.getQueryObject())
			.containsEntry("_id", "job-1")
			.containsEntry("status", ImportJobStatus.PROCESSING)
			.containsEntry("leaseOwner", "worker-1"));

		Document progressSet = (Document) updates.getAllValues().get(1).getUpdateObject().get("$set");
		assertThat(progressSet)
			.containsEntry("totalRows", 3L)
			.containsEntry("successRows", 3L)
			.containsEntry("failureRows", 0L);
		Document finishSet = (Document) updates.getAllValues().get(2).getUpdateObject().get("$set");
		Document finishUnset = (Document) updates.getAllValues().get(2).getUpdateObject().get("$unset");
		assertThat(finishSet).containsEntry("status", ImportJobStatus.COMPLETED);
		assertThat(finishUnset).containsKeys("leaseOwner", "leaseExpiresAt", "heartbeatAt");
	}
}

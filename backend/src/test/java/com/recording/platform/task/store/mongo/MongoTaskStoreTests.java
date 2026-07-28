package com.recording.platform.task.store.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.task.model.TaskRecord;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class MongoTaskStoreTests {
	@Test
	void allocationAtomicallyRaisesAnOutdatedCounterAboveTheObservedMaximum() {
		SpringDataTaskRepository repository = org.mockito.Mockito.mock(SpringDataTaskRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		TaskRecord updated = new TaskRecord();
		updated.setItemSequence(84L);
		when(template.findAndModify(
			any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(TaskRecord.class)
		)).thenReturn(updated);
		MongoTaskStore store = new MongoTaskStore(repository, template);

		long sequence = store.nextItemSequence("task-1", 83L);

		assertThat(sequence).isEqualTo(84L);
		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
		verify(template).findAndModify(
			query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(TaskRecord.class)
		);
		assertThat(query.getValue().getQueryObject()).containsEntry("_id", "task-1");
		assertThat(update.getValue()).isInstanceOf(AggregationUpdate.class);
		Document stage = ((AggregationUpdate) update.getValue()).getPipeline().getOperations().stream()
			.map(operation -> operation.toDocument(Aggregation.DEFAULT_CONTEXT))
			.map(operation -> (Document) operation.get("$set"))
			.filter(set -> set != null && set.containsKey("itemSequence"))
			.findFirst()
			.orElseThrow();
		assertThat(stage.get("itemSequence").toString())
			.contains("$add", "$max", "$ifNull", "$itemSequence", "83", "1");
	}

	@Test
	void repeatedAllocationsUseSeparateAtomicUpdatesAndKeepTheReturnedSequenceOrder() {
		SpringDataTaskRepository repository = org.mockito.Mockito.mock(SpringDataTaskRepository.class);
		MongoTemplate template = org.mockito.Mockito.mock(MongoTemplate.class);
		TaskRecord first = new TaskRecord();
		first.setItemSequence(84L);
		TaskRecord second = new TaskRecord();
		second.setItemSequence(85L);
		when(template.findAndModify(
			any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(TaskRecord.class)
		)).thenReturn(first, second);
		MongoTaskStore store = new MongoTaskStore(repository, template);

		long firstSequence = store.nextItemSequence("task-1", 83L);
		long secondSequence = store.nextItemSequence("task-1", 83L);

		assertThat(firstSequence).isEqualTo(84L);
		assertThat(secondSequence).isEqualTo(85L);
		verify(template, times(2)).findAndModify(
			any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(TaskRecord.class)
		);
	}
}

package com.recording.platform.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recording.platform.voice.dto.VoiceGenerationConfigRequest;
import com.recording.platform.voice.model.VoiceGenerationConfig;
import com.recording.platform.voice.model.VoiceGenerationRecord;
import com.recording.platform.voice.repository.MongoVoiceGenerationRecordStore;
import com.recording.platform.voice.repository.SpringDataVoiceGenerationConfigRepository;
import com.recording.platform.voice.repository.SpringDataVoiceGenerationRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class MongoVoiceGenerationPersistenceTests {

	@Test
	void mongoRecordAdapterPersistsAndReadsRecordsInRecentOrder() {
		SpringDataVoiceGenerationRecordRepository repository = org.mockito.Mockito.mock(
			SpringDataVoiceGenerationRecordRepository.class
		);
		VoiceGenerationRecord record = new VoiceGenerationRecord();
		record.setId("record-1");
		when(repository.save(record)).thenReturn(record);
		when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(1, 20)))
			.thenReturn(new PageImpl<>(List.of(record)));
		when(repository.count()).thenReturn(41L);
		MongoVoiceGenerationRecordStore store = new MongoVoiceGenerationRecordStore(repository);

		assertThat(store.save(record)).isSameAs(record);
		assertThat(store.findRecent(1, 20)).containsExactly(record);
		assertThat(store.count()).isEqualTo(41L);
	}

	@Test
	void defaultVoiceConfigurationIsReadAndWrittenThroughMongoRepository() {
		SpringDataVoiceGenerationConfigRepository repository = org.mockito.Mockito.mock(
			SpringDataVoiceGenerationConfigRepository.class
		);
		when(repository.findById("default")).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		Clock clock = Clock.fixed(Instant.parse("2026-07-11T12:00:00Z"), ZoneOffset.UTC);
		VoiceGenerationConfigService service = new VoiceGenerationConfigService(repository, clock);

		assertThat(service.defaultConfig().getVoiceId()).isEqualTo("sichuan_native_01");
		VoiceGenerationConfig saved = service.saveDefaultConfig(new VoiceGenerationConfigRequest(
			"voice-2",
			1.1,
			1.2,
			1
		));

		ArgumentCaptor<VoiceGenerationConfig> captor = ArgumentCaptor.forClass(VoiceGenerationConfig.class);
		verify(repository).save(captor.capture());
		assertThat(saved.getId()).isEqualTo("default");
		assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-11T12:00:00Z"));
		assertThat(captor.getValue().getVoiceId()).isEqualTo("voice-2");
	}
}

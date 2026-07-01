package com.recording.platform.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.voice.dto.SynthesisRequest;
import com.recording.platform.voice.dto.VoiceGenerationResponse;
import com.recording.platform.voice.integration.MiniMaxSynthesisResult;
import com.recording.platform.voice.integration.MiniMaxVoiceClient;
import com.recording.platform.voice.model.GenerationMode;
import com.recording.platform.voice.model.GenerationStatus;
import com.recording.platform.voice.model.VoiceGenerationRecord;
import com.recording.platform.voice.repository.VoiceGenerationRecordStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class VoiceGenerationServiceTests {

	@TempDir
	Path tempDir;

	@Test
	void synthesizeSavesAudioFileAndGenerationRecord() throws IOException {
		FakeMiniMaxVoiceClient miniMaxClient = new FakeMiniMaxVoiceClient();
		InMemoryRecordStore recordStore = new InMemoryRecordStore();
		VoiceGenerationService service = createService(miniMaxClient, recordStore);

		VoiceGenerationResponse response = service.synthesize(new SynthesisRequest(
			"voice-sichuan-01",
			"今天天气很好，适合录音。",
			0.9,
			1.0,
			0
		));

		assertThat(response.status()).isEqualTo(GenerationStatus.COMPLETED);
		assertThat(response.audioUrl()).isEqualTo("/api/voice-generation/audio/record-1");
		assertThat(Files.readAllBytes(tempDir.resolve("record-1.mp3"))).isEqualTo(new byte[] {1, 2, 3, 4});
		assertThat(recordStore.saved).hasSize(1);
		VoiceGenerationRecord saved = recordStore.saved.get(0);
		assertThat(saved.getMode()).isEqualTo(GenerationMode.SYNTHESIZE);
		assertThat(saved.getText()).isEqualTo("今天天气很好，适合录音。");
		assertThat(saved.getVoiceId()).isEqualTo("voice-sichuan-01");
		assertThat(saved.getAudioPath()).isEqualTo(tempDir.resolve("record-1.mp3").toString());
	}

	@Test
	void previewUploadsPromptAudioBeforeSynthesis() {
		FakeMiniMaxVoiceClient miniMaxClient = new FakeMiniMaxVoiceClient();
		InMemoryRecordStore recordStore = new InMemoryRecordStore();
		VoiceGenerationService service = createService(miniMaxClient, recordStore);
		MockMultipartFile audio = new MockMultipartFile("audio", "sample.m4a", "audio/m4a", new byte[] {5, 6});

		VoiceGenerationResponse response = service.preview(audio, "试听一句四川话。", 1.0, 1.0, 0);

		assertThat(response.status()).isEqualTo(GenerationStatus.COMPLETED);
		assertThat(miniMaxClient.uploadPurposes).containsExactly("prompt_audio");
		assertThat(miniMaxClient.promptFileIdUsed).isEqualTo("prompt-file-1");
		assertThat(recordStore.saved.get(0).getMode()).isEqualTo(GenerationMode.PREVIEW);
	}

	private VoiceGenerationService createService(
		FakeMiniMaxVoiceClient miniMaxClient,
		InMemoryRecordStore recordStore
	) {
		Clock clock = Clock.fixed(Instant.parse("2026-06-23T15:30:00Z"), ZoneOffset.UTC);
		VoiceGenerationStorage storage = new VoiceGenerationStorage(tempDir);
		return new VoiceGenerationService(miniMaxClient, recordStore, storage, clock);
	}

	private static final class FakeMiniMaxVoiceClient implements MiniMaxVoiceClient {
		private final List<String> uploadPurposes = new ArrayList<>();
		private String promptFileIdUsed;
		private int synthesizeCalls;

		@Override
		public String uploadAudio(org.springframework.web.multipart.MultipartFile audio, String purpose) {
			uploadPurposes.add(purpose);
			return "prompt-file-1";
		}

		@Override
		public void cloneVoice(String fileId, String voiceId) {
		}

		@Override
		public MiniMaxSynthesisResult synthesize(SynthesisRequest request, String promptFileId) {
			synthesizeCalls++;
			promptFileIdUsed = promptFileId;
			return new MiniMaxSynthesisResult(new byte[] {1, 2, 3, 4}, "mp3", 4);
		}

		@Override
		public Map<String, Object> listVoices(boolean excludeSystemVoices) {
			return Map.of();
		}

		@Override
		public void deleteVoice(String voiceId) {
		}
	}

	private static final class InMemoryRecordStore implements VoiceGenerationRecordStore {
		private final List<VoiceGenerationRecord> saved = new ArrayList<>();

		@Override
		public VoiceGenerationRecord save(VoiceGenerationRecord record) {
			if (record.getId() == null) {
				record.setId("record-" + (saved.size() + 1));
			}
			saved.removeIf((savedRecord) -> savedRecord.getId().equals(record.getId()));
			saved.add(record);
			return record;
		}

		@Override
		public Optional<VoiceGenerationRecord> findById(String id) {
			return saved.stream().filter((record) -> record.getId().equals(id)).findFirst();
		}
	}

}

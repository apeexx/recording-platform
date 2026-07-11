package com.recording.platform.voice;

import com.recording.platform.api.ApiException;
import com.recording.platform.voice.dto.SynthesisRequest;
import com.recording.platform.voice.dto.VoiceGenerationResponse;
import com.recording.platform.voice.integration.MiniMaxSynthesisResult;
import com.recording.platform.voice.integration.MiniMaxVoiceClient;
import com.recording.platform.voice.model.GenerationMode;
import com.recording.platform.voice.model.GenerationStatus;
import com.recording.platform.voice.model.VoiceGenerationRecord;
import com.recording.platform.voice.repository.VoiceGenerationRecordStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceGenerationService {
	private final MiniMaxVoiceClient miniMaxVoiceClient;
	private final VoiceGenerationRecordStore recordStore;
	private final VoiceGenerationStorage storage;
	private final Clock clock;

	public VoiceGenerationService(
		MiniMaxVoiceClient miniMaxVoiceClient,
		VoiceGenerationRecordStore recordStore,
		VoiceGenerationStorage storage,
		Clock clock
	) {
		this.miniMaxVoiceClient = miniMaxVoiceClient;
		this.recordStore = recordStore;
		this.storage = storage;
		this.clock = clock;
	}

	public VoiceGenerationResponse synthesize(SynthesisRequest request) {
		return synthesizeWithMode(request, null, GenerationMode.SYNTHESIZE);
	}

	public VoiceGenerationResponse preview(MultipartFile audio, String text, double speed, double volume, int pitch) {
		String promptFileId = miniMaxVoiceClient.uploadAudio(audio, "prompt_audio");
		SynthesisRequest request = new SynthesisRequest("male-qn-qingse", text, speed, volume, pitch);
		return synthesizeWithMode(request, promptFileId, GenerationMode.PREVIEW);
	}

	public void cloneVoice(MultipartFile audio, String voiceId) {
		if (audio.getSize() > 20L * 1024 * 1024) {
			throw new ApiException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"CLONE_AUDIO_TOO_LARGE",
				"克隆母带音频不能超过 20MB"
			);
		}
		String fileId = miniMaxVoiceClient.uploadAudio(audio, "voice_clone");
		miniMaxVoiceClient.cloneVoice(fileId, voiceId);
		VoiceGenerationRecord record = new VoiceGenerationRecord();
		record.setMode(GenerationMode.CLONE);
		record.setStatus(GenerationStatus.COMPLETED);
		record.setVoiceId(voiceId);
		record.setCreatedAt(Instant.now(clock));
		record.setMessage("音色克隆已提交");
		recordStore.save(record);
	}

	public Map<String, Object> listVoices(boolean excludeSystem) {
		return miniMaxVoiceClient.listVoices(excludeSystem);
	}

	public void deleteVoice(String voiceId) {
		miniMaxVoiceClient.deleteVoice(voiceId);
	}

	public Map<String, Object> listRecords(int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		List<VoiceGenerationRecord> records = recordStore.findRecent(safePage, safeSize);
		List<Map<String, Object>> items = records.stream().map(this::toRecordMap).toList();
		return Map.of(
			"items", items,
			"page", safePage,
			"size", safeSize,
			"total", recordStore.count()
		);
	}

	public VoiceGenerationAudio loadAudio(String recordId) {
		VoiceGenerationRecord record = recordStore.findById(recordId)
			.orElseThrow(() -> new VoiceGenerationException("生成记录不存在"));
		Path audioPath = record.getAudioPath() == null ? null : Path.of(record.getAudioPath());
		String format = record.getAudioFormat() == null ? "mp3" : record.getAudioFormat();
		String contentType = "mp3".equalsIgnoreCase(format) ? "audio/mpeg" : "audio/" + format;
		return new VoiceGenerationAudio(storage.load(audioPath), record.getId() + "." + format, contentType);
	}

	private VoiceGenerationResponse synthesizeWithMode(
		SynthesisRequest request,
		String promptFileId,
		GenerationMode mode
	) {
		VoiceGenerationRecord completed = buildRecord(request, mode, GenerationStatus.PENDING);
		completed = saveRecord(completed);
		MiniMaxSynthesisResult result;
		try {
			result = miniMaxVoiceClient.synthesize(request, promptFileId);
		} catch (RuntimeException exception) {
			completed.setStatus(GenerationStatus.FAILED);
			completed.setMessage("MiniMax 合成失败");
			saveRecord(completed);
			throw exception;
		}
		completed.setStatus(GenerationStatus.COMPLETED);
		completed.setMessage("生成成功");
		Path audioPath = storage.save(completed.getId(), result.format(), result.audioBytes());
		completed.setAudioPath(audioPath.toString());
		completed.setAudioFormat(result.format());
		completed.setDurationMillis(result.durationMillis());
		completed = saveRecord(completed);
		return toResponse(completed);
	}

	private VoiceGenerationRecord buildRecord(
		SynthesisRequest request,
		GenerationMode mode,
		GenerationStatus status
	) {
		VoiceGenerationRecord record = new VoiceGenerationRecord();
		record.setMode(mode);
		record.setStatus(status);
		record.setText(request.text());
		record.setVoiceId(request.voiceId());
		record.setSpeed(request.speed());
		record.setVolume(request.volume());
		record.setPitch(request.pitch());
		record.setCreatedAt(Instant.now(clock));
		record.setMessage("处理中");
		return record;
	}

	private VoiceGenerationResponse toResponse(VoiceGenerationRecord record) {
		String audioUrl = record.getAudioPath() == null ? null : "/api/voice-generation/audio/" + record.getId();
		return new VoiceGenerationResponse(
			record.getId(),
			record.getMode(),
			record.getStatus(),
			record.getMessage(),
			audioUrl
		);
	}

	private Map<String, Object> toRecordMap(VoiceGenerationRecord record) {
		return Map.of(
			"id", nullToEmpty(record.getId()),
			"mode", record.getMode(),
			"status", record.getStatus(),
			"text", nullToEmpty(record.getText()),
			"voiceId", nullToEmpty(record.getVoiceId()),
			"message", nullToEmpty(record.getMessage()),
			"audioUrl", record.getAudioPath() == null ? "" : "/api/voice-generation/audio/" + record.getId(),
			"createdAt", record.getCreatedAt() == null ? "" : record.getCreatedAt().toString()
		);
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private VoiceGenerationRecord saveRecord(VoiceGenerationRecord record) {
		return recordStore.save(record);
	}
}

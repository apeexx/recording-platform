package com.recording.platform.voice;

import com.recording.platform.voice.dto.SynthesisRequest;
import com.recording.platform.voice.dto.VoiceGenerationResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice-generation")
public class VoiceGenerationController {
	private final VoiceGenerationService service;

	public VoiceGenerationController(VoiceGenerationService service) {
		this.service = service;
	}

	@PostMapping("/preview")
	public VoiceGenerationResponse preview(
		@RequestParam("audio") MultipartFile audio,
		@RequestParam("text") String text,
		@RequestParam(defaultValue = "1.0") double speed,
		@RequestParam(defaultValue = "1.0") double volume,
		@RequestParam(defaultValue = "0") int pitch
	) {
		return service.preview(audio, text, speed, volume, pitch);
	}

	@PostMapping("/synthesize")
	public VoiceGenerationResponse synthesize(@Valid @RequestBody SynthesisRequest request) {
		return service.synthesize(request);
	}

	@PostMapping("/voices/clone")
	public Map<String, Object> cloneVoice(
		@RequestParam("audio") MultipartFile audio,
		@RequestParam("voiceId") String voiceId
	) {
		service.cloneVoice(audio, voiceId);
		return Map.of("success", true, "message", "音色克隆已提交");
	}

	@GetMapping("/voices")
	public Map<String, Object> voices(@RequestParam(defaultValue = "true") boolean excludeSystem) {
		return service.listVoices(excludeSystem);
	}

	@DeleteMapping("/voices/{voiceId}")
	public Map<String, Object> deleteVoice(@PathVariable String voiceId) {
		service.deleteVoice(voiceId);
		return Map.of("success", true, "message", "音色已删除");
	}

	@GetMapping("/records")
	public Map<String, Object> records(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		return service.listRecords(page, size);
	}

	@GetMapping("/audio/{recordId}")
	public ResponseEntity<Resource> audio(@PathVariable String recordId) {
		VoiceGenerationAudio audio = service.loadAudio(recordId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(audio.contentType()))
			.header(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.inline().filename(audio.filename()).build().toString()
			)
			.body(audio.resource());
	}
}

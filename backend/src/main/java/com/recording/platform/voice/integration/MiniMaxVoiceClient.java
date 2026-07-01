package com.recording.platform.voice.integration;

import com.recording.platform.voice.dto.SynthesisRequest;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface MiniMaxVoiceClient {
	String uploadAudio(MultipartFile audio, String purpose);

	void cloneVoice(String fileId, String voiceId);

	MiniMaxSynthesisResult synthesize(SynthesisRequest request, String promptFileId);

	Map<String, Object> listVoices(boolean excludeSystemVoices);

	void deleteVoice(String voiceId);
}

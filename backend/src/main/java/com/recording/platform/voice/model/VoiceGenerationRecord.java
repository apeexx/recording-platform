package com.recording.platform.voice.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "voice_generation_records")
public class VoiceGenerationRecord {
	@Id
	private String id;
	private GenerationMode mode;
	private GenerationStatus status;
	private String text;
	private String voiceId;
	private double speed;
	private double volume;
	private int pitch;
	private String audioPath;
	private String audioFormat;
	private long durationMillis;
	private String message;
	@Indexed
	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public GenerationMode getMode() {
		return mode;
	}

	public void setMode(GenerationMode mode) {
		this.mode = mode;
	}

	public GenerationStatus getStatus() {
		return status;
	}

	public void setStatus(GenerationStatus status) {
		this.status = status;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getVoiceId() {
		return voiceId;
	}

	public void setVoiceId(String voiceId) {
		this.voiceId = voiceId;
	}

	public double getSpeed() {
		return speed;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}

	public double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public int getPitch() {
		return pitch;
	}

	public void setPitch(int pitch) {
		this.pitch = pitch;
	}

	public String getAudioPath() {
		return audioPath;
	}

	public void setAudioPath(String audioPath) {
		this.audioPath = audioPath;
	}

	public String getAudioFormat() {
		return audioFormat;
	}

	public void setAudioFormat(String audioFormat) {
		this.audioFormat = audioFormat;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}

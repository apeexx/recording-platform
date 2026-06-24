package com.recording.platform.voice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_generation_records")
public class VoiceGenerationRecord {
	@Id
	@Column(length = 36)
	private String id;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private GenerationMode mode;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private GenerationStatus status;
	@Column(columnDefinition = "text")
	private String text;
	@Column(name = "voice_id", length = 128)
	private String voiceId;
	private double speed;
	private double volume;
	private int pitch;
	@Column(name = "audio_path", columnDefinition = "text")
	private String audioPath;
	@Column(name = "audio_format", length = 16)
	private String audioFormat;
	@Column(name = "duration_millis")
	private long durationMillis;
	@Column(columnDefinition = "text")
	private String message;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void ensureId() {
		if (id == null || id.isBlank()) {
			id = UUID.randomUUID().toString();
		}
	}

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

package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.RecordingFormat;
import com.recording.platform.task.model.TaskVersion;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class RecordingMediaStorageTests {
	@TempDir
	Path tempDir;

	@Test
	void validMonoWavIsValidatedIntoATemporaryFileAndAtomicallyActivated() throws Exception {
		RecordingMediaStorage storage = new RecordingMediaStorage(tempDir);
		TaskVersion version = wavVersion();
		MockMultipartFile upload = new MockMultipartFile(
			"audio", "voice.wav", "audio/wav", wav(16000, 1, 2000, (byte) 7)
		);

		PreparedRecording prepared = storage.prepare(upload, version, "TASK-001", "I000001");
		assertThat(prepared.recording().relativePath())
			.isEqualTo("recordings/TASK-001/I000001/current.wav")
			.doesNotContain(tempDir.toString());
		assertThat(prepared.recording().sampleRate()).isEqualTo(16000);
		assertThat(prepared.recording().channels()).isEqualTo(1);
		assertThat(prepared.recording().durationMillis()).isBetween(1999L, 2001L);

		RecordingReplacement replacement = storage.activate(prepared, null);
		replacement.complete();
		assertThat(storage.resolve(prepared.recording().relativePath())).exists();
	}

	@Test
	void invalidMagicOrTaskAudioParametersFailWithoutReplacingThePreviousFile() throws Exception {
		RecordingMediaStorage storage = new RecordingMediaStorage(tempDir);
		TaskVersion version = wavVersion();
		PreparedRecording first = storage.prepare(
			new MockMultipartFile("audio", "voice.wav", "audio/wav", wav(16000, 1, 2000, (byte) 3)),
			version,
			"TASK-001",
			"I000001"
		);
		RecordingReplacement active = storage.activate(first, null);
		active.complete();
		Path current = storage.resolve(first.recording().relativePath());
		byte[] original = Files.readAllBytes(current);

		assertThatThrownBy(() -> storage.prepare(
			new MockMultipartFile("audio", "fake.wav", "audio/wav", "not-a-wave".getBytes()),
			version,
			"TASK-001",
			"I000001"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("INVALID_AUDIO_FILE")
		);
		assertThatThrownBy(() -> storage.prepare(
			new MockMultipartFile("audio", "stereo.wav", "audio/wav", wav(16000, 2, 2000, (byte) 5)),
			version,
			"TASK-001",
			"I000001"
		)).isInstanceOfSatisfying(ApiException.class, (exception) ->
			assertThat(exception.getCode()).isEqualTo("INVALID_AUDIO_CHANNELS")
		);
		assertThat(Files.readAllBytes(current)).isEqualTo(original);
	}

	@Test
	void rollbackRestoresThePreviousCurrentFileAndTraversalIsRejected() throws Exception {
		RecordingMediaStorage storage = new RecordingMediaStorage(tempDir);
		TaskVersion version = wavVersion();
		PreparedRecording first = storage.prepare(
			new MockMultipartFile("audio", "first.wav", "audio/wav", wav(16000, 1, 2000, (byte) 1)),
			version, "TASK-001", "I000001"
		);
		RecordingReplacement committed = storage.activate(first, null);
		committed.complete();
		Path current = storage.resolve(first.recording().relativePath());
		byte[] original = Files.readAllBytes(current);

		PreparedRecording second = storage.prepare(
			new MockMultipartFile("audio", "second.wav", "audio/wav", wav(16000, 1, 2000, (byte) 9)),
			version, "TASK-001", "I000001"
		);
		RecordingReplacement replacement = storage.activate(second, first.recording().relativePath());
		replacement.rollback();

		assertThat(Files.readAllBytes(current)).isEqualTo(original);
		assertThatThrownBy(() -> storage.resolve("../outside.wav"))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("PATH_TRAVERSAL_BLOCKED")
			);
	}

	@Test
	void repeatedRecordingsKeepTheStableCurrentPathAndDiscardTheOldFileAfterCommit() throws Exception {
		RecordingMediaStorage storage = new RecordingMediaStorage(tempDir);
		TaskVersion version = wavVersion();
		PreparedRecording previous = storage.prepare(
			new MockMultipartFile("audio", "previous.wav", "audio/wav", wav(16000, 1, 2000, (byte) 1)),
			version, "TASK-001", "I000001"
		);
		RecordingReplacement previousReplacement = storage.activate(previous, null);
		previousReplacement.complete();
		PreparedRecording first = storage.prepare(
			new MockMultipartFile("audio", "first.wav", "audio/wav", wav(16000, 1, 2000, (byte) 2)),
			version, "TASK-001", "I000001"
		);
		PreparedRecording second = storage.prepare(
			new MockMultipartFile("audio", "second.wav", "audio/wav", wav(16000, 1, 2000, (byte) 3)),
			version, "TASK-001", "I000001"
		);

		assertThat(first.recording().relativePath()).isEqualTo("recordings/TASK-001/I000001/current.wav");
		assertThat(second.recording().relativePath()).isEqualTo(first.recording().relativePath());
		RecordingReplacement firstReplacement = storage.activate(first, previous.recording().relativePath());
		firstReplacement.complete();
		RecordingReplacement secondReplacement = storage.activate(second, first.recording().relativePath());
		secondReplacement.complete();

		assertThat(storage.resolve(second.recording().relativePath())).exists();
		assertThat(Files.readAllBytes(storage.resolve(second.recording().relativePath())))
			.isEqualTo(wav(16000, 1, 2000, (byte) 3));
		Path backups = tempDir.resolve("temp/backups");
		if (Files.exists(backups)) {
			try (var files = Files.walk(backups)) {
				assertThat(files.filter(Files::isRegularFile)).isEmpty();
			}
		}
	}

	@Test
	void deleteFailureReturnsAStorageErrorInsteadOfPretendingSuccess() throws Exception {
		RecordingMediaStorage storage = new RecordingMediaStorage(tempDir);
		String relative = "recordings/TASK-001/I000001/current.wav";
		Path undeletable = storage.resolve(relative);
		Files.createDirectories(undeletable);
		Files.writeString(undeletable.resolve("child.tmp"), "still present");

		assertThatThrownBy(() -> storage.delete(relative))
			.isInstanceOfSatisfying(ApiException.class, (exception) -> {
				assertThat(exception.getStatus().value()).isEqualTo(500);
				assertThat(exception.getCode()).isEqualTo("MEDIA_DELETE_FAILED");
			});
	}

	private TaskVersion wavVersion() {
		TaskVersion version = new TaskVersion();
		version.setRecordingFormat(RecordingFormat.WAV);
		version.setSampleRates(Set.of(16000));
		version.setChannels(1);
		version.setMinDurationMillis(1000);
		version.setMaxDurationMillis(3000);
		return version;
	}

	private byte[] wav(int sampleRate, int channels, int durationMillis, byte fill) {
		int bitsPerSample = 16;
		int dataLength = sampleRate * channels * bitsPerSample / 8 * durationMillis / 1000;
		ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
		header.put(new byte[] {'R', 'I', 'F', 'F'});
		header.putInt(36 + dataLength);
		header.put(new byte[] {'W', 'A', 'V', 'E'});
		header.put(new byte[] {'f', 'm', 't', ' '});
		header.putInt(16);
		header.putShort((short) 1);
		header.putShort((short) channels);
		header.putInt(sampleRate);
		header.putInt(sampleRate * channels * bitsPerSample / 8);
		header.putShort((short) (channels * bitsPerSample / 8));
		header.putShort((short) bitsPerSample);
		header.put(new byte[] {'d', 'a', 't', 'a'});
		header.putInt(dataLength);
		ByteArrayOutputStream output = new ByteArrayOutputStream(44 + dataLength);
		output.writeBytes(header.array());
		output.writeBytes(new byte[dataLength]);
		byte[] bytes = output.toByteArray();
		bytes[bytes.length - 1] = fill;
		return bytes;
	}
}

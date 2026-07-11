package com.recording.platform.media;

import com.mpatric.mp3agic.Mp3File;
import com.recording.platform.api.ApiException;
import com.recording.platform.task.model.RecordingFormat;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.http.HttpStatus;

final class AudioMetadataInspector {
	AudioMetadata inspect(Path path, String originalFilename) {
		String extension = extension(originalFilename);
		try {
			return switch (extension) {
				case "wav" -> inspectWav(path);
				case "mp3" -> inspectMp3(path);
				default -> throw invalid("录音文件只支持 WAV 或 MP3");
			};
		} catch (ApiException exception) {
			throw exception;
		} catch (Exception exception) {
			throw invalid("录音文件无法解析或已损坏");
		}
	}

	private AudioMetadata inspectWav(Path path) throws IOException {
		try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
			if (input.length() < 44 || !"RIFF".equals(readAscii(input, 4))) throw invalid("WAV 魔数不合法");
			readLittleEndianInt(input);
			if (!"WAVE".equals(readAscii(input, 4))) throw invalid("WAV 魔数不合法");
			Integer channels = null;
			Integer sampleRate = null;
			Integer byteRate = null;
			Long dataSize = null;
			while (input.getFilePointer() + 8 <= input.length()) {
				String chunk = readAscii(input, 4);
				long size = Integer.toUnsignedLong(readLittleEndianInt(input));
				long next = input.getFilePointer() + size + (size % 2);
				if (next > input.length() + 1) throw invalid("WAV 分块长度不合法");
				if ("fmt ".equals(chunk)) {
					int encoding = readLittleEndianShort(input);
					if (encoding != 1) throw invalid("WAV 必须为 PCM 编码");
					channels = readLittleEndianShort(input);
					sampleRate = readLittleEndianInt(input);
					byteRate = readLittleEndianInt(input);
				} else if ("data".equals(chunk)) {
					dataSize = size;
				}
				input.seek(Math.min(next, input.length()));
			}
			if (channels == null || sampleRate == null || byteRate == null || byteRate <= 0 || dataSize == null) {
				throw invalid("WAV 缺少必要音频分块");
			}
			long duration = Math.round(dataSize * 1000.0 / byteRate);
			return new AudioMetadata(RecordingFormat.WAV, sampleRate, channels, duration);
		}
	}

	private AudioMetadata inspectMp3(Path path) throws Exception {
		byte[] prefix;
		try (var input = Files.newInputStream(path)) {
			prefix = input.readNBytes(3);
		}
		if (prefix.length < 3) throw invalid("MP3 魔数不合法");
		boolean id3 = prefix[0] == 'I' && prefix[1] == 'D' && prefix[2] == '3';
		boolean frame = (prefix[0] & 0xff) == 0xff && (prefix[1] & 0xe0) == 0xe0;
		if (!id3 && !frame) throw invalid("MP3 魔数不合法");
		Mp3File mp3 = new Mp3File(path.toString());
		int channels = "Mono".equalsIgnoreCase(mp3.getChannelMode()) ? 1 : 2;
		return new AudioMetadata(RecordingFormat.MP3, mp3.getSampleRate(), channels, mp3.getLengthInMilliseconds());
	}

	private String extension(String filename) {
		if (filename == null) return "";
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String readAscii(RandomAccessFile input, int length) throws IOException {
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		return new String(bytes, StandardCharsets.US_ASCII);
	}

	private int readLittleEndianShort(RandomAccessFile input) throws IOException {
		int low = input.readUnsignedByte();
		int high = input.readUnsignedByte();
		return low | (high << 8);
	}

	private int readLittleEndianInt(RandomAccessFile input) throws IOException {
		return readLittleEndianShort(input) | (readLittleEndianShort(input) << 16);
	}

	private ApiException invalid(String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AUDIO_FILE", message);
	}
}

package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import org.junit.jupiter.api.Test;

class ReferenceMediaUrlValidatorTests {
	private final ReferenceMediaUrlValidator validator = new ReferenceMediaUrlValidator();

	@Test
	void acceptsAbsoluteHttpsUrlsWithoutInspectingRemoteContent() {
		assertThat(validator.validateNullable(
			"https://oss.example.com/media/object?signature=abc#fragment"
		)).isEqualTo("https://oss.example.com/media/object?signature=abc#fragment");
		assertThat(validator.validateNullable("  https://cdn.example.com/audio  "))
			.isEqualTo("https://cdn.example.com/audio");
		assertThat(validator.validateNullable(" ")).isNull();
	}

	@Test
	void rejectsNonHttpsRelativeHostlessAndCredentialUrls() {
		assertInvalid("http://example.com/audio.mp3");
		assertInvalid("/relative/audio.mp3");
		assertInvalid("https:///audio.mp3");
		assertInvalid("https://user:password@example.com/audio.mp3");
	}

	private void assertInvalid(String value) {
		assertThatThrownBy(() -> validator.validateNullable(value))
			.isInstanceOfSatisfying(ApiException.class, exception ->
				assertThat(exception.getCode()).isEqualTo("REMOTE_URL_INVALID"));
	}
}

package com.recording.platform.media;

import java.nio.file.Path;

public record ReadableMedia(Path path, String contentType, long length) {
}

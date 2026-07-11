package com.recording.platform.importing;

public record StoredImportSource(String relativePath, String sha256, long sizeBytes) {
}

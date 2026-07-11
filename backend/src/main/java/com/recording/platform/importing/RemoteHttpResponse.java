package com.recording.platform.importing;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RemoteHttpResponse implements AutoCloseable {
	private final int status;
	private final Map<String, List<String>> headers;
	private final InputStream body;

	public RemoteHttpResponse(int status, Map<String, List<String>> headers, InputStream body) {
		this.status = status;
		this.headers = new LinkedHashMap<>();
		headers.forEach((name, values) -> this.headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
		this.body = body;
	}

	public int status() { return status; }
	public InputStream body() { return body; }
	public String firstHeader(String name) {
		List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
		return values == null || values.isEmpty() ? null : values.get(0);
	}

	@Override
	public void close() throws IOException {
		body.close();
	}
}

package com.recording.platform.media;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

public record ResolvedRemote(URI uri, String hostname, List<InetAddress> addresses) {
	public ResolvedRemote {
		addresses = List.copyOf(addresses);
	}
}

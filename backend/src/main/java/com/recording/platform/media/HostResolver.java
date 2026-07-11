package com.recording.platform.media;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

@FunctionalInterface
public interface HostResolver {
	List<InetAddress> resolve(String hostname) throws IOException;
}

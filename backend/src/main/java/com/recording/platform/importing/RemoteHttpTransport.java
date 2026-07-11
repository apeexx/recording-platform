package com.recording.platform.importing;

import com.recording.platform.media.ResolvedRemote;
import java.time.Duration;

public interface RemoteHttpTransport {
	RemoteHttpResponse get(ResolvedRemote remote, Duration timeout);
}

package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RemoteUrlPolicy {
	private final boolean allowHttp;
	private final HostResolver resolver;

	@Autowired
	public RemoteUrlPolicy(@Value("${recording.remote-media.allow-http:false}") boolean allowHttp) {
		this(allowHttp, (hostname) -> Arrays.asList(InetAddress.getAllByName(hostname)));
	}

	public RemoteUrlPolicy(boolean allowHttp, HostResolver resolver) {
		this.allowHttp = allowHttp;
		this.resolver = resolver;
	}

	public ResolvedRemote resolve(URI uri) {
		if (uri == null || uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null) {
			throw blocked("REMOTE_URL_INVALID", "远程媒体 URL 不合法");
		}
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
			throw blocked("REMOTE_URL_SCHEME_BLOCKED", "远程媒体默认只允许 HTTPS");
		}
		String hostname = uri.getHost().toLowerCase(Locale.ROOT);
		if (hostname.equals("localhost") || hostname.endsWith(".localhost")) {
			throw blocked("REMOTE_HOST_BLOCKED", "远程媒体地址不能指向本机或私网");
		}
		List<InetAddress> addresses;
		try {
			addresses = resolver.resolve(hostname);
		} catch (IOException exception) {
			throw blocked("REMOTE_HOST_UNRESOLVED", "远程媒体主机无法解析");
		}
		if (addresses == null || addresses.isEmpty() || addresses.stream().anyMatch(this::isBlocked)) {
			throw blocked("REMOTE_HOST_BLOCKED", "远程媒体地址不能指向本机或私网");
		}
		return new ResolvedRemote(uri.normalize(), hostname, addresses);
	}

	public void verifyStable(URI uri, ResolvedRemote expected) {
		ResolvedRemote current = resolve(uri);
		Set<String> expectedAddresses = addressStrings(expected.addresses());
		Set<String> currentAddresses = addressStrings(current.addresses());
		if (!expected.hostname().equals(current.hostname()) || !expectedAddresses.equals(currentAddresses)) {
			throw blocked("DNS_REBINDING_BLOCKED", "远程媒体主机解析结果在下载期间发生变化");
		}
	}

	private Set<String> addressStrings(List<InetAddress> addresses) {
		Set<String> values = new HashSet<>();
		for (InetAddress address : addresses) values.add(address.getHostAddress());
		return values;
	}

	private boolean isBlocked(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
			|| address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
		byte[] bytes = address.getAddress();
		if (address instanceof Inet6Address) {
			int first = bytes[0] & 0xff;
			return (first & 0xfe) == 0xfc || first == 0xff;
		}
		int first = bytes[0] & 0xff;
		int second = bytes[1] & 0xff;
		return first == 0 || first >= 224
			|| first == 10 || first == 127
			|| (first == 100 && second >= 64 && second <= 127)
			|| (first == 169 && second == 254)
			|| (first == 172 && second >= 16 && second <= 31)
			|| (first == 192 && second == 168)
			|| (first == 198 && (second == 18 || second == 19));
	}

	private ApiException blocked(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
	}
}

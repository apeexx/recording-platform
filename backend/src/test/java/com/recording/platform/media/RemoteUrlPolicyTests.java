package com.recording.platform.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recording.platform.api.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class RemoteUrlPolicyTests {
	@Test
	void productionAllowsHttpsPublicHostsAndRejectsHttpLocalhostAndPrivateAddresses() throws Exception {
		RemoteUrlPolicy policy = new RemoteUrlPolicy(false, (host) -> switch (host) {
			case "cdn.example.com" -> List.of(InetAddress.getByName("93.184.216.34"));
			case "private.example.com" -> List.of(InetAddress.getByName("10.0.0.8"));
			default -> List.of(InetAddress.getByName("127.0.0.1"));
		});

		ResolvedRemote resolved = policy.resolve(URI.create("https://cdn.example.com/audio.wav"));
		assertThat(resolved.hostname()).isEqualTo("cdn.example.com");
		assertThatThrownBy(() -> policy.resolve(URI.create("http://cdn.example.com/audio.wav")))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("REMOTE_URL_SCHEME_BLOCKED")
			);
		assertThatThrownBy(() -> policy.resolve(URI.create("https://localhost/audio.wav")))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("REMOTE_HOST_BLOCKED")
			);
		assertThatThrownBy(() -> policy.resolve(URI.create("https://private.example.com/audio.wav")))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("REMOTE_HOST_BLOCKED")
			);
	}

	@Test
	void explicitDevelopmentHttpStillBlocksPrivateNetworks() throws Exception {
		RemoteUrlPolicy policy = new RemoteUrlPolicy(true, (host) ->
			List.of(InetAddress.getByName(host.equals("dev.example.com") ? "93.184.216.34" : "192.168.1.2"))
		);

		assertThat(policy.resolve(URI.create("http://dev.example.com/reference.mp3")).hostname())
			.isEqualTo("dev.example.com");
		assertThatThrownBy(() -> policy.resolve(URI.create("http://nas.example.com/reference.mp3")))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void dnsAnswerChangesAreRejectedAsRebinding() throws Exception {
		Queue<List<InetAddress>> answers = new ArrayDeque<>();
		answers.add(List.of(InetAddress.getByName("93.184.216.34")));
		answers.add(List.of(InetAddress.getByName("93.184.216.35")));
		RemoteUrlPolicy policy = new RemoteUrlPolicy(false, (host) -> answers.remove());
		URI uri = URI.create("https://cdn.example.com/audio.wav");
		ResolvedRemote initial = policy.resolve(uri);

		assertThatThrownBy(() -> policy.verifyStable(uri, initial))
			.isInstanceOfSatisfying(ApiException.class, (exception) ->
				assertThat(exception.getCode()).isEqualTo("DNS_REBINDING_BLOCKED")
			);
	}
}

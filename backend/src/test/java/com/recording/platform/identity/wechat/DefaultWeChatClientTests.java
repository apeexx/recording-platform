package com.recording.platform.identity.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefaultWeChatClientTests {

	@Test
	void exchangesTemporaryCodeWhenWechatReturnsJsonAsTextPlain() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		DefaultWeChatClient client = new DefaultWeChatClient(
			new WeChatSettings("test-app-id", "test-app-secret"),
			builder.build()
		);
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(
			"https://api.weixin.qq.com/sns/jscode2session"
		)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("appid", "test-app-id"))
			.andExpect(queryParam("secret", "test-app-secret"))
			.andExpect(queryParam("js_code", "temporary-code"))
			.andExpect(queryParam("grant_type", "authorization_code"))
			.andRespond(withSuccess("{\"openid\":\"openid-from-wechat\",\"session_key\":\"not-persisted\"}", MediaType.TEXT_PLAIN));

		WeChatIdentity identity = client.exchange("temporary-code");

		assertThat(identity).isEqualTo(new WeChatIdentity("test-app-id", "openid-from-wechat"));
		server.verify();
	}
}

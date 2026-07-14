package com.recording.platform.identity.wechat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recording.platform.api.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class DefaultWeChatClient implements WeChatClient {
	private static final ObjectMapper JSON = new ObjectMapper();
	private final WeChatSettings settings;
	private final RestClient restClient;

	public DefaultWeChatClient(WeChatSettings settings) {
		this(settings, RestClient.builder().baseUrl("https://api.weixin.qq.com").build());
	}

	DefaultWeChatClient(WeChatSettings settings, RestClient restClient) {
		this.settings = settings;
		this.restClient = restClient;
	}

	@Override
	public WeChatIdentity exchange(String code) {
		if (!StringUtils.hasText(settings.appId()) || !StringUtils.hasText(settings.appSecret())) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_NOT_CONFIGURED", "微信登录配置缺失");
		}
		if (!StringUtils.hasText(code)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_CODE_REQUIRED", "微信临时登录凭证不能为空");
		}
		try {
			String body = restClient.get()
				.uri((builder) -> builder.path("/sns/jscode2session")
					.queryParam("appid", settings.appId())
					.queryParam("secret", settings.appSecret())
					.queryParam("js_code", code)
					.queryParam("grant_type", "authorization_code")
					.build())
				.retrieve()
				.body(String.class);
			Map<String, Object> response = StringUtils.hasText(body)
				? JSON.readValue(body, new TypeReference<>() {
				})
				: Map.of();
			if (response == null || response.get("openid") == null) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "WECHAT_LOGIN_FAILED", "微信登录凭证无效");
			}
			return new WeChatIdentity(settings.appId(), response.get("openid").toString());
		} catch (ApiException exception) {
			throw exception;
		} catch (JsonProcessingException | RestClientException exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_UNAVAILABLE", "微信登录服务暂时不可用");
		}
	}
}

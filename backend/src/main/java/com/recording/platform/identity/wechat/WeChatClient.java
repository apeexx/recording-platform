package com.recording.platform.identity.wechat;

public interface WeChatClient {
	WeChatIdentity exchange(String code);
}

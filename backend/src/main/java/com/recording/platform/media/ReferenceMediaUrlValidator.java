package com.recording.platform.media;

import com.recording.platform.api.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ReferenceMediaUrlValidator {
	public String validateNullable(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim();
		try {
			URI uri = new URI(normalized);
			if (!uri.isAbsolute()
				|| !"https".equalsIgnoreCase(uri.getScheme())
				|| uri.getHost() == null
				|| uri.getHost().isBlank()
				|| uri.getUserInfo() != null) {
				throw invalid();
			}
			return normalized;
		} catch (URISyntaxException exception) {
			throw invalid();
		}
	}

	private ApiException invalid() {
		return new ApiException(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"REMOTE_URL_INVALID",
			"参考媒体 URL 必须是包含有效主机且不含认证信息的绝对 HTTPS 地址"
		);
	}
}

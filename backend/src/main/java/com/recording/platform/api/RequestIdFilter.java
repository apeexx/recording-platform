package com.recording.platform.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	public static final String HEADER_NAME = "X-Request-Id";
	public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = normalize(request.getHeader(HEADER_NAME));
		request.setAttribute(ATTRIBUTE_NAME, requestId);
		response.setHeader(HEADER_NAME, requestId);
		filterChain.doFilter(request, response);
	}

	public static String currentRequestId(HttpServletRequest request) {
		Object value = request.getAttribute(ATTRIBUTE_NAME);
		if (value instanceof String requestId && StringUtils.hasText(requestId)) {
			return requestId;
		}
		String generated = UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE_NAME, generated);
		return generated;
	}

	private String normalize(String candidate) {
		if (!StringUtils.hasText(candidate) || candidate.length() > 128) {
			return UUID.randomUUID().toString();
		}
		String sanitized = candidate.replaceAll("[^A-Za-z0-9._:-]", "");
		return StringUtils.hasText(sanitized) ? sanitized : UUID.randomUUID().toString();
	}
}

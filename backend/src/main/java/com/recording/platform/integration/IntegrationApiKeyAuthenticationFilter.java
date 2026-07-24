package com.recording.platform.integration;

import com.recording.platform.api.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IntegrationApiKeyAuthenticationFilter extends OncePerRequestFilter {
	public static final String HEADER_NAME = "X-API-Key";
	public static final String PRINCIPAL_NAME = "INTEGRATION-ANNOTATION-SCRIPT-CENTER";
	public static final String AUTHORITY = "ROLE_INTEGRATION_IMPORT";
	private static final String WRITE_PATH_PATTERN = "/api/integrations/tasks/[^/]+/items";
	private static final String READ_PATH_PATTERN = "/api/integrations/items/[^/]+(?:/audio)?";

	private final IntegrationApiKeyAuthenticator authenticator;
	private final ApiErrorWriter errorWriter;

	public IntegrationApiKeyAuthenticationFilter(
		IntegrationApiKeyAuthenticator authenticator,
		ApiErrorWriter errorWriter
	) {
		this.authenticator = authenticator;
		this.errorWriter = errorWriter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getRequestURI();
		boolean write = "POST".equalsIgnoreCase(method) && path.matches(WRITE_PATH_PATTERN);
		boolean read = "GET".equalsIgnoreCase(method) && path.matches(READ_PATH_PATTERN);
		return !write && !read;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (!authenticator.isConfigured()) {
			errorWriter.write(
				request,
				response,
				HttpStatus.SERVICE_UNAVAILABLE,
				"INTEGRATION_NOT_CONFIGURED",
				"外部集成接口尚未配置"
			);
			return;
		}
		if (!authenticator.matches(request.getHeader(HEADER_NAME))) {
			errorWriter.write(
				request,
				response,
				HttpStatus.UNAUTHORIZED,
				"INVALID_INTEGRATION_API_KEY",
				"外部集成凭证无效"
			);
			return;
		}
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			PRINCIPAL_NAME,
			null,
			List.of(new SimpleGrantedAuthority(AUTHORITY))
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}
}

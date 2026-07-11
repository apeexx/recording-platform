package com.recording.platform.security;

import com.recording.platform.api.ApiErrorWriter;
import com.recording.platform.identity.model.SessionType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirstPasswordChangeFilter extends OncePerRequestFilter {
	private static final Set<String> ALLOWED_PATHS = Set.of(
		"/api/auth/web/me",
		"/api/auth/web/csrf",
		"/api/auth/web/logout",
		"/api/auth/web/password"
	);
	private final ApiErrorWriter errorWriter;

	public FirstPasswordChangeFilter(ApiErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
			&& authentication.getPrincipal() instanceof PlatformPrincipal principal
			&& principal.sessionType() == SessionType.WEB
			&& principal.firstPasswordChangeRequired()
			&& request.getRequestURI().startsWith("/api/")
			&& !ALLOWED_PATHS.contains(request.getRequestURI())) {
			errorWriter.write(
				request,
				response,
				HttpStatus.FORBIDDEN,
				"PASSWORD_CHANGE_REQUIRED",
				"首次登录必须先修改密码"
			);
			return;
		}
		filterChain.doFilter(request, response);
	}
}

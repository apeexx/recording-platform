package com.recording.platform.security;

import com.recording.platform.api.ApiException;
import com.recording.platform.identity.service.SessionIdentity;
import com.recording.platform.identity.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
	public static final String WEB_COOKIE_NAME = "REC_WEB_SESSION";
	private final SessionService sessions;
	private final boolean secureCookie;
	private final Duration webCookieLifetime;

	public SessionAuthenticationFilter(
		SessionService sessions,
		@Value("${recording.web-session.cookie-secure:false}") boolean secureCookie,
		@Value("${recording.web-session.idle-hours:12}") long webIdleHours
	) {
		this.sessions = sessions;
		this.secureCookie = secureCookie;
		this.webCookieLifetime = Duration.ofHours(Math.max(webIdleHours, 1));
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String authenticatedWebToken = null;
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticatedWebToken = tryAuthenticate(request);
		}
		if (StringUtils.hasText(authenticatedWebToken) && shouldRenewWebCookie(request)) {
			response.addHeader(HttpHeaders.SET_COOKIE, renewedCookie(authenticatedWebToken).toString());
		}
		filterChain.doFilter(request, response);
	}

	private String tryAuthenticate(HttpServletRequest request) {
		String webToken = cookieValue(request, WEB_COOKIE_NAME);
		String miniProgramToken = bearerToken(request);
		if (!StringUtils.hasText(webToken) && !StringUtils.hasText(miniProgramToken)) {
			return null;
		}
		try {
			SessionIdentity identity = StringUtils.hasText(webToken)
				? sessions.authenticateWeb(webToken)
				: sessions.authenticateMiniProgram(miniProgramToken);
			PlatformPrincipal principal = PlatformPrincipal.from(identity);
			List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
			SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, authorities)
			);
			return StringUtils.hasText(webToken) ? webToken : null;
		} catch (ApiException exception) {
			request.setAttribute(SecurityErrorAttributes.CODE, exception.getCode());
			request.setAttribute(SecurityErrorAttributes.MESSAGE, exception.getMessage());
			return null;
		}
	}

	private boolean shouldRenewWebCookie(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !"/api/auth/web/logout".equals(path) && !"/api/auth/web/password".equals(path);
	}

	private ResponseCookie renewedCookie(String token) {
		return ResponseCookie.from(WEB_COOKIE_NAME, token)
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite("Lax")
			.path("/")
			.maxAge(webCookieLifetime)
			.build();
	}

	private String cookieValue(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
			.filter((cookie) -> name.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private String bearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
			return authorization.substring(7).trim();
		}
		return null;
	}
}

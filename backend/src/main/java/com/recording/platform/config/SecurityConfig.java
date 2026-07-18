package com.recording.platform.config;

import com.recording.platform.api.ApiErrorWriter;
import com.recording.platform.security.FirstPasswordChangeFilter;
import com.recording.platform.security.SecurityErrorAttributes;
import com.recording.platform.security.SessionAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		SessionAuthenticationFilter sessionAuthenticationFilter,
		FirstPasswordChangeFilter firstPasswordChangeFilter,
		ApiErrorWriter errorWriter
	) throws Exception {
		CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfTokens.setCookiePath("/");
		RequestMatcher miniProgramTaskWrite = (request) -> {
			String authorization = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
			if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")
				|| hasCookie(request.getCookies(), SessionAuthenticationFilter.WEB_COOKIE_NAME)
				|| !"POST".equalsIgnoreCase(request.getMethod())) return false;
			String path = request.getRequestURI();
			return path.matches("/api/tasks/[^/]+/items/start")
				|| path.matches("/api/tasks/[^/]+/access-requests")
				|| path.matches("/api/task-items/[^/]+/(submit|release)");
		};
		http.csrf((csrf) -> csrf
			.csrfTokenRepository(csrfTokens)
			.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
			.ignoringRequestMatchers(
				new AntPathRequestMatcher("/api/auth/web/login", "POST"),
				new AntPathRequestMatcher("/api/auth/web/takeover", "POST"),
				new AntPathRequestMatcher("/api/auth/miniprogram/**"),
				miniProgramTaskWrite
			)
		);
		http.httpBasic((basic) -> basic.disable());
		http.formLogin((form) -> form.disable());
		http.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.exceptionHandling((exceptions) -> exceptions
			.authenticationEntryPoint((request, response, exception) -> {
				String code = attribute(request, SecurityErrorAttributes.CODE, "AUTHENTICATION_REQUIRED");
				String message = attribute(request, SecurityErrorAttributes.MESSAGE, "请先登录");
				errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, code, message);
			})
			.accessDeniedHandler((request, response, exception) -> {
				boolean csrfInvalid = exception instanceof MissingCsrfTokenException
					|| exception instanceof InvalidCsrfTokenException;
				errorWriter.write(
					request,
					response,
					HttpStatus.FORBIDDEN,
					csrfInvalid ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED",
					csrfInvalid ? "安全令牌已失效，请重试" : "没有权限执行此操作"
				);
			})
		);
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(HttpMethod.GET, "/api/health/ready").permitAll()
			.requestMatchers(HttpMethod.POST,
				"/api/auth/web/login",
				"/api/auth/web/takeover",
				"/api/auth/miniprogram/login"
			).permitAll()
			.requestMatchers("/api/import-jobs/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.POST, "/api/reviews/claim", "/api/reviews/claim-batch", "/api/reviews/*/release")
				.hasRole("REVIEWER")
			.requestMatchers(HttpMethod.POST, "/api/reviews/assign", "/api/reviews/batch/approve")
				.hasRole("ADMIN")
			.requestMatchers("/api/reviews/**").hasAnyRole("ADMIN", "REVIEWER")
			.requestMatchers(HttpMethod.POST, "/api/task-items/*/status", "/api/task-items/*/discard", "/api/task-items/*/restore")
				.hasRole("ADMIN")
			.requestMatchers(HttpMethod.POST, "/api/task-items/batch/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.POST, "/api/tasks/*/items/start", "/api/tasks/*/access-requests")
				.hasRole("COLLECTOR")
			.requestMatchers(HttpMethod.POST, "/api/task-items/*/submit").hasRole("COLLECTOR")
			.requestMatchers(HttpMethod.POST, "/api/task-items/*/release").hasAnyRole("ADMIN", "COLLECTOR")
			.requestMatchers(HttpMethod.POST, "/api/task-items/*/reject").hasAnyRole("ADMIN", "REVIEWER")
			.requestMatchers(HttpMethod.POST, "/api/tasks", "/api/tasks/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.PUT, "/api/tasks/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.GET, "/api/tasks/*/items", "/api/tasks/*/grants", "/api/tasks/*/access-requests")
				.hasRole("ADMIN")
			.requestMatchers("/api/voice-generation/**", "/api/admin/**").hasRole("ADMIN")
			.requestMatchers(HttpMethod.GET, "/api/operations").hasAnyRole("ADMIN", "REVIEWER")
			.requestMatchers(HttpMethod.GET, "/api/reports/tasks", "/api/reports/collectors").hasRole("ADMIN")
			.requestMatchers(HttpMethod.GET, "/api/reports/reviewers").hasAnyRole("ADMIN", "REVIEWER")
			.requestMatchers("/api/auth/miniprogram/**").hasRole("COLLECTOR")
			.requestMatchers("/api/**").authenticated()
			.anyRequest().permitAll()
		);
		http.addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		http.addFilterAfter(firstPasswordChangeFilter, SessionAuthenticationFilter.class);
		return http.build();
	}

	private static String attribute(jakarta.servlet.http.HttpServletRequest request, String name, String fallback) {
		Object value = request.getAttribute(name);
		return value instanceof String text && !text.isBlank() ? text : fallback;
	}

	private static boolean hasCookie(Cookie[] cookies, String name) {
		if (cookies == null) return false;
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) return true;
		}
		return false;
	}

	@Bean
	FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilterRegistration(
		SessionAuthenticationFilter filter
	) {
		FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<FirstPasswordChangeFilter> firstPasswordChangeFilterRegistration(
		FirstPasswordChangeFilter filter
	) {
		FilterRegistrationBean<FirstPasswordChangeFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}

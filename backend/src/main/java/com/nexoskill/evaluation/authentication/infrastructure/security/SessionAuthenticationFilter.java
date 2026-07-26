package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.domain.model.AuthSession;
import com.nexoskill.evaluation.authentication.domain.model.SessionStatus;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private static final Set<String> PUBLIC_PATHS = Set.of("/api/v1/auth/login", "/api/v1/student-auth/login",
			"/actuator/health", "/error");

	private static final Set<String> PASSWORD_CHANGE_ALLOWED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/logout",
			"/api/v1/auth/change-password", "/api/v1/users/me", "/actuator/health", "/error");

	private final SessionCookieSupport cookieSupport;
	private final TokenHasher tokenHasher;
	private final AuthSessionRepository sessionRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public SessionAuthenticationFilter(SessionCookieSupport cookieSupport, TokenHasher tokenHasher,
			AuthSessionRepository sessionRepository, UserRepository userRepository, Clock clock) {
		this.cookieSupport = cookieSupport;
		this.tokenHasher = tokenHasher;
		this.sessionRepository = sessionRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/api/v1/student-auth") || path.startsWith("/api/v1/student/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (SecurityContextHolder.getContext().getAuthentication() == null && !authenticate(request, response)) {
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String rawToken = cookieSupport.readToken(request);
		if (rawToken == null || rawToken.isBlank()) {
			return true;
		}

		Instant now = clock.instant();
		AuthSession session = sessionRepository.findByTokenHash(tokenHasher.hash(rawToken)).orElse(null);

		if (session == null) {
			clearCookie(response);
			return true;
		}

		if (!session.isActiveAt(now)) {
			if (session.getStatus() == SessionStatus.ACTIVE) {
				session.expire();
				sessionRepository.save(session);
			}

			UserAccount expiredSessionUser = userRepository.findById(session.getUserId()).orElse(null);
			boolean temporaryPasswordExpired = expiredSessionUser != null
					&& expiredSessionUser.isTemporaryPasswordExpiredAt(now);

			clearCookie(response);
			return publicRequest(request) || reject(response, HttpServletResponse.SC_UNAUTHORIZED,
					temporaryPasswordExpired ? "TEMP_PASSWORD_EXPIRED" : "SESSION_EXPIRED",
					temporaryPasswordExpired ? "La contraseña temporal ha expirado." : "La sesión ha vencido.");
		}

		UserAccount user = userRepository.findById(session.getUserId()).orElse(null);
		if (user == null) {
			session.revoke(now);
			sessionRepository.save(session);
			clearCookie(response);
			return publicRequest(request)
					|| reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "La sesión no es válida.");
		}

		UserAccessStatus accessStatus = user.getAccess().effectiveStatusAt(now);
		if (accessStatus == UserAccessStatus.EXPIRED) {
			session.revoke(now);
			sessionRepository.save(session);
			clearCookie(response);
			return publicRequest(request) || reject(response, HttpServletResponse.SC_UNAUTHORIZED, "ACCESS_EXPIRED",
					"Tu acceso a la plataforma ha expirado.");
		}

		if (!user.canAuthenticateAt(now)) {
			session.revoke(now);
			sessionRepository.save(session);
			clearCookie(response);
			return publicRequest(request) || reject(response, HttpServletResponse.SC_UNAUTHORIZED,
					"ACCOUNT_UNAVAILABLE", "Tu cuenta no está disponible.");
		}

		if (user.isTemporaryPasswordExpiredAt(now)) {
			session.revoke(now);
			sessionRepository.save(session);
			clearCookie(response);
			return publicRequest(request) || reject(response, HttpServletResponse.SC_UNAUTHORIZED,
					"TEMP_PASSWORD_EXPIRED", "La contraseña temporal ha expirado.");
		}

		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getPublicId(), user.getEmail(),
				user.getFirstName(), user.getLastName(), user.getDisplayName(),
				user.getRoles().stream().map(role -> role.code())
						.collect(java.util.stream.Collectors.toUnmodifiableSet()),
				user.permissions(), user.getLastLoginAt(), accessStatus, user.getAccess().startsAt(),
				user.getAccess().expiresAt(), user.isPasswordChangeRequired(), user.getPasswordChangedAt(),
				user.getTemporaryPasswordExpiresAt());

		List<SimpleGrantedAuthority> authorities = java.util.stream.Stream
				.concat(principal.roles().stream().map(role -> "ROLE_" + role), principal.permissions().stream())
				.map(SimpleGrantedAuthority::new).toList();

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
				authorities);

		SecurityContextHolder.getContext().setAuthentication(authentication);

		if (user.isPasswordChangeRequired() && !PASSWORD_CHANGE_ALLOWED_PATHS.contains(request.getRequestURI())) {
			return reject(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
					"Debes cambiar tu contraseña para continuar.");
		}

		return true;
	}

	private boolean publicRequest(HttpServletRequest request) {
		return PUBLIC_PATHS.contains(request.getRequestURI());
	}

	private void clearCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());
	}

	private boolean reject(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message));
		return false;
	}
}

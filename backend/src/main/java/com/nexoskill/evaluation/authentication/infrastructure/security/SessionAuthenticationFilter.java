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

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/actuator/health",
            "/error"
    );

    private final SessionCookieSupport cookieSupport;
    private final TokenHasher tokenHasher;
    private final AuthSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SessionAuthenticationFilter(
            SessionCookieSupport cookieSupport,
            TokenHasher tokenHasher,
            AuthSessionRepository sessionRepository,
            UserRepository userRepository,
            Clock clock) {
        this.cookieSupport = cookieSupport;
        this.tokenHasher = tokenHasher;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && !authenticate(request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticate(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String rawToken = cookieSupport.readToken(request);
        if (rawToken == null || rawToken.isBlank()) {
            return true;
        }

        Instant now = clock.instant();
        AuthSession session = sessionRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .orElse(null);

        if (session == null) {
            clearCookie(response);
            return true;
        }

        if (!session.isActiveAt(now)) {
            if (session.getStatus() == SessionStatus.ACTIVE) {
                session.expire();
                sessionRepository.save(session);
            }
            clearCookie(response);
            return publicRequest(request)
                    || reject(response, "SESSION_EXPIRED", "La sesión ha vencido.");
        }

        UserAccount user = userRepository.findById(session.getUserId()).orElse(null);
        if (user == null) {
            session.revoke(now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request)
                    || reject(response, "UNAUTHORIZED", "La sesión no es válida.");
        }

        UserAccessStatus accessStatus = user.getAccess().effectiveStatusAt(now);
        if (accessStatus == UserAccessStatus.EXPIRED) {
            session.revoke(now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request)
                    || reject(
                            response,
                            "ACCESS_EXPIRED",
                            "Tu acceso a la plataforma ha expirado."
                    );
        }

        if (!user.canAuthenticateAt(now)) {
            session.revoke(now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request)
                    || reject(
                            response,
                            "ACCOUNT_UNAVAILABLE",
                            "Tu cuenta no está disponible."
                    );
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getPublicId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getRoles().stream().map(role -> role.code())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                user.permissions(),
                user.getLastLoginAt(),
                accessStatus,
                user.getAccess().startsAt(),
                user.getAccess().expiresAt()
        );

        List<SimpleGrantedAuthority> authorities = java.util.stream.Stream.concat(
                        principal.roles().stream().map(role -> "ROLE_" + role),
                        principal.permissions().stream()
                )
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private boolean publicRequest(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieSupport.clear().toString()
        );
    }

    private boolean reject(
            HttpServletResponse response,
            String code,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                String.format(
                        "{\"code\":\"%s\",\"message\":\"%s\"}",
                        code,
                        message
                )
        );
        return false;
    }
}

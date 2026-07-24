package com.nexoskill.evaluation.authentication.infrastructure.security;

import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.domain.model.AuthSession;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

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

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String rawToken = cookieSupport.readToken(request);
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        Instant now = clock.instant();
        AuthSession session = sessionRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .filter(candidate -> candidate.isActiveAt(now))
                .orElse(null);

        if (session == null) {
            return;
        }

        UserAccount user = userRepository.findById(session.getUserId())
                .filter(candidate -> candidate.canAuthenticateAt(now))
                .orElse(null);

        if (user == null) {
            return;
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
                user.getLastLoginAt()
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
    }
}

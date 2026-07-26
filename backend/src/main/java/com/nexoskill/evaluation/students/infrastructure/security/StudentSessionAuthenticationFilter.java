package com.nexoskill.evaluation.students.infrastructure.security;

import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.students.application.StudentAuthenticationService;
import com.nexoskill.evaluation.students.domain.StudentEffectiveStatus;
import com.nexoskill.evaluation.students.domain.StudentSessionRevocationReason;
import com.nexoskill.evaluation.students.domain.StudentSessionStatus;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class StudentSessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/student-auth/login", "/actuator/health", "/error");
    private static final Set<String> PASSWORD_CHANGE_PATHS = Set.of(
            "/api/v1/student-auth/logout", "/api/v1/student-auth/change-password", "/api/v1/student/me");

    private final StudentSessionCookieSupport cookieSupport;
    private final TokenHasher tokenHasher;
    private final StudentSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final OrganizationRepository organizationRepository;
    private final StudentAuthenticationService authenticationService;
    private final Clock clock;

    public StudentSessionAuthenticationFilter(StudentSessionCookieSupport cookieSupport, TokenHasher tokenHasher,
            StudentSessionRepository sessionRepository, StudentRepository studentRepository,
            OrganizationRepository organizationRepository, StudentAuthenticationService authenticationService,
            Clock clock) {
        this.cookieSupport = cookieSupport;
        this.tokenHasher = tokenHasher;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.organizationRepository = organizationRepository;
        this.authenticationService = authenticationService;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/student-auth") || path.startsWith("/api/v1/student/"));
    }

    @Override
    @Transactional
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            if (!authenticate(request, response)) return;
        }
        chain.doFilter(request, response);
    }

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String rawToken = cookieSupport.readToken(request);
        if (rawToken == null || rawToken.isBlank()) return true;
        Instant now = clock.instant();
        StudentSessionJpaEntity session = sessionRepository.findByTokenHash(tokenHasher.hash(rawToken)).orElse(null);
        if (session == null) {
            clearCookie(response);
            return true;
        }
        if (!session.isActiveAt(now)) {
            if (session.getStatus() == StudentSessionStatus.ACTIVE) {
                session.expire(now);
                sessionRepository.save(session);
            }
            clearCookie(response);
            return publicRequest(request) || reject(response, "STUDENT_SESSION_EXPIRED", "La sesión ha vencido.");
        }
        StudentJpaEntity student = studentRepository.findById(session.getStudentId()).orElse(null);
        OrganizationJpaEntity organization = organizationRepository.findById(session.getOrganizationId()).orElse(null);
        if (student == null || organization == null
                || !session.getOrganizationId().equals(student.getOrganizationId())) {
            session.revoke(StudentSessionRevocationReason.ADMIN_REVOKED, now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request) || reject(response, "STUDENT_UNAUTHORIZED", "La sesión no es válida.");
        }
        if (!organization.isOperational(LocalDate.now(clock))) {
            session.revoke(StudentSessionRevocationReason.ORGANIZATION_UNAVAILABLE, now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request) || reject(response, "STUDENT_ACCOUNT_UNAVAILABLE",
                    "La organización no está disponible.");
        }
        if (student.isTemporaryPasswordExpiredAt(now)) {
            session.revoke(StudentSessionRevocationReason.TEMP_PASSWORD_EXPIRED, now);
            sessionRepository.save(session);
            clearCookie(response);
            return publicRequest(request) || reject(response, "STUDENT_TEMP_PASSWORD_EXPIRED",
                    "La contraseña temporal ha expirado.");
        }
        StudentEffectiveStatus status = student.effectiveStatusAt(now);
        if (!student.canAuthenticateAt(now)) {
            session.revoke(revocationReason(status), now);
            sessionRepository.save(session);
            clearCookie(response);
            String code = status == StudentEffectiveStatus.EXPIRED ? "STUDENT_ACCESS_EXPIRED"
                    : "STUDENT_ACCOUNT_UNAVAILABLE";
            return publicRequest(request) || reject(response, code,
                    status == StudentEffectiveStatus.EXPIRED ? "Tu acceso ha vencido." : "La cuenta no está disponible.");
        }
        AuthenticatedStudent principal = authenticationService.principal(student, organization, now);
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_STUDENT"),
                new SimpleGrantedAuthority("STUDENT_PORTAL"),
                new SimpleGrantedAuthority("STUDENT_PASSWORD_CHANGE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
        if (student.isPasswordChangeRequired() && !PASSWORD_CHANGE_PATHS.contains(request.getRequestURI())) {
            return reject(response, "STUDENT_PASSWORD_CHANGE_REQUIRED",
                    "Debes cambiar tu contraseña para continuar.", HttpServletResponse.SC_FORBIDDEN);
        }
        return true;
    }

    private StudentSessionRevocationReason revocationReason(StudentEffectiveStatus status) {
        return switch (status) {
            case EXPIRED -> StudentSessionRevocationReason.EXPIRED;
            case SUSPENDED -> StudentSessionRevocationReason.SUSPENDED;
            case ARCHIVED -> StudentSessionRevocationReason.ARCHIVED;
            case DELETED -> StudentSessionRevocationReason.DELETED;
            default -> StudentSessionRevocationReason.DEACTIVATED;
        };
    }

    private boolean publicRequest(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieSupport.clear().toString());
    }

    private boolean reject(HttpServletResponse response, String code, String message) throws IOException {
        return reject(response, code, message, HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean reject(HttpServletResponse response, String code, String message, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message));
        return false;
    }
}

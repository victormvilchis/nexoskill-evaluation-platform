package com.nexoskill.evaluation.authentication.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final AuthSessionRepository sessionRepository;
    private final TokenHasher tokenHasher;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public LogoutService(
            AuthSessionRepository sessionRepository,
            TokenHasher tokenHasher,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public void logout(
            String rawToken,
            Long userId,
            String ipAddress,
            String userAgent) {

        Instant now = clock.instant();
        if (rawToken != null && !rawToken.isBlank()) {
            sessionRepository.findByTokenHash(tokenHasher.hash(rawToken))
                    .ifPresent(session -> {
                        session.revoke(now);
                        sessionRepository.save(session);
                    });
        }

        auditLogPort.record(
                userId,
                "LOGOUT_SUCCEEDED",
                "AUTHENTICATION",
                "El usuario cerró sesión.",
                ipAddress,
                userAgent,
                Map.of(),
                now
        );
    }
}

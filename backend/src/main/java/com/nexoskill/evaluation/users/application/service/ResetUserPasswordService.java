package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.application.model.ResetUserPasswordCommand;
import com.nexoskill.evaluation.users.application.model.TemporaryPasswordResult;
import com.nexoskill.evaluation.users.application.port.out.PasswordHistoryPort;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetUserPasswordService {
    private final UserManagementPort userManagementPort;
    private final UserRepository userRepository;
    private final UserSessionPort userSessionPort;
    private final PasswordHistoryPort passwordHistoryPort;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final SecureTemporaryPasswordGenerator passwordGenerator;
    private final AuditLogPort auditLogPort;
    private final AppProperties properties;
    private final Clock clock;

    public ResetUserPasswordService(UserManagementPort userManagementPort, UserRepository userRepository,
            UserSessionPort userSessionPort, PasswordHistoryPort passwordHistoryPort, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, SecureTemporaryPasswordGenerator passwordGenerator,
            AuditLogPort auditLogPort, AppProperties properties, Clock clock) {
        this.userManagementPort = userManagementPort;
        this.userRepository = userRepository;
        this.userSessionPort = userSessionPort;
        this.passwordHistoryPort = passwordHistoryPort;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordGenerator = passwordGenerator;
        this.auditLogPort = auditLogPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TemporaryPasswordResult reset(ResetUserPasswordCommand command) {
        var managedUser = userManagementPort.getByPublicId(command.publicId());
        UserAccount user = userRepository.findById(managedUser.internalId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario solicitado no existe."));
        String temporaryPassword = generateValidTemporaryPassword(user);

        Instant now = clock.instant();
        passwordHistoryPort.record(user.getId(), user.getPasswordHash(), now);
        Instant expiresAt = now.plus(properties.getSecurity().getTemporaryPasswordDuration());
        var updated = userManagementPort.updatePassword(command.publicId(), passwordHasher.encode(temporaryPassword),
                expiresAt);
        int revokedSessions = userSessionPort.revokeActiveSessions(managedUser.internalId(), now);

        var data = new LinkedHashMap<String, Object>();
        data.put("targetUserPublicId", updated.publicId());
        data.put("targetEmail", updated.email());
        data.put("revokedSessions", revokedSessions);
        data.put("temporaryPasswordExpiresAt", expiresAt.toString());
        auditLogPort.record(command.actorUserId(), "USER_PASSWORD_RESET", "USER_MANAGEMENT",
                "Se generó una nueva contraseña temporal de visualización única.", command.ipAddress(),
                command.userAgent(), data, now);
        return new TemporaryPasswordResult(updated, temporaryPassword);
    }

    private String generateValidTemporaryPassword(UserAccount user) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = passwordGenerator.generate();
            try {
                passwordPolicy.validate(candidate, user.getEmail());
                rejectReusedPassword(user, candidate);
                return candidate;
            } catch (BusinessException exception) {
                if (!"PASSWORD_CONTAINS_EMAIL".equals(exception.getCode())
                        && !"PASSWORD_REUSE_NOT_ALLOWED".equals(exception.getCode())) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("No fue posible generar una contraseña temporal segura.");
    }

    private void rejectReusedPassword(UserAccount user, String password) {
        if (passwordHasher.matches(password, user.getPasswordHash())) {
            throw reuseException();
        }
        int historySize = properties.getSecurity().getPasswordHistorySize();
        boolean reused = passwordHistoryPort.recentHashes(user.getId(), historySize).stream()
                .anyMatch(hash -> passwordHasher.matches(password, hash));
        if (reused) {
            throw reuseException();
        }
    }

    private BusinessException reuseException() {
        return new BusinessException("PASSWORD_REUSE_NOT_ALLOWED",
                "No puedes reutilizar una de las últimas contraseñas del usuario.");
    }
}

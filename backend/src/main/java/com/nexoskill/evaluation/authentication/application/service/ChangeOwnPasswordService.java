package com.nexoskill.evaluation.authentication.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.model.ChangePasswordCommand;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.application.port.out.PasswordHistoryPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeOwnPasswordService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHistoryPort passwordHistoryPort;
    private final UserSessionPort userSessionPort;
    private final AuditLogPort auditLogPort;
    private final AppProperties properties;
    private final Clock clock;

    public ChangeOwnPasswordService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            PasswordHistoryPort passwordHistoryPort,
            UserSessionPort userSessionPort,
            AuditLogPort auditLogPort,
            AppProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistoryPort = passwordHistoryPort;
        this.userSessionPort = userSessionPort;
        this.auditLogPort = auditLogPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void change(ChangePasswordCommand command) {
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new BusinessException(
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "La confirmación de la contraseña no coincide."
            );
        }

        UserAccount user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "La cuenta solicitada no existe."
                ));

        if (!passwordHasher.matches(
                command.currentPassword(),
                user.getPasswordHash())) {
            throw new BusinessException(
                    "CURRENT_PASSWORD_INVALID",
                    "La contraseña actual es incorrecta."
            );
        }

        passwordPolicy.validate(command.newPassword(), user.getEmail());
        rejectReusedPassword(user, command.newPassword());

        boolean passwordChangeRequiredBefore = user.isPasswordChangeRequired();
        Instant now = clock.instant();
        passwordHistoryPort.record(
                user.getId(),
                user.getPasswordHash(),
                now
        );

        user.changePassword(
                passwordHasher.encode(command.newPassword()),
                now
        );
        userRepository.save(user);

        int revokedSessions = userSessionPort.revokeOtherActiveSessions(
                user.getId(),
                command.currentSessionTokenHash(),
                now
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("revokedSessions", revokedSessions);
        data.put("passwordChangeRequiredBefore", passwordChangeRequiredBefore);

        auditLogPort.record(
                user.getId(),
                "PASSWORD_CHANGED",
                "PROFILE",
                "El usuario cambió su contraseña.",
                command.ipAddress(),
                command.userAgent(),
                data,
                now
        );
    }

    private void rejectReusedPassword(UserAccount user, String newPassword) {
        if (passwordHasher.matches(newPassword, user.getPasswordHash())) {
            throw reuseException();
        }

        int historySize = properties.getSecurity().getPasswordHistorySize();
        boolean reused = passwordHistoryPort.recentHashes(
                        user.getId(),
                        historySize
                ).stream()
                .anyMatch(hash -> passwordHasher.matches(newPassword, hash));

        if (reused) {
            throw reuseException();
        }
    }

    private BusinessException reuseException() {
        return new BusinessException(
                "PASSWORD_REUSE_NOT_ALLOWED",
                "No puedes reutilizar una de tus últimas contraseñas."
        );
    }
}

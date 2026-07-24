package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.ResetUserPasswordCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetUserPasswordService {

    private final UserManagementPort userManagementPort;
    private final UserSessionPort userSessionPort;
    private final PasswordHasher passwordHasher;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public ResetUserPasswordService(
            UserManagementPort userManagementPort,
            UserSessionPort userSessionPort,
            PasswordHasher passwordHasher,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.userManagementPort = userManagementPort;
        this.userSessionPort = userSessionPort;
        this.passwordHasher = passwordHasher;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary reset(ResetUserPasswordCommand command) {
        UserManagementSupport.validatePassword(command.temporaryPassword());
        UserManagementPort.ManagedUser managedUser =
                userManagementPort.getByPublicId(command.publicId());
        Instant now = clock.instant();

        AdminUserSummary updated = userManagementPort.updatePassword(
                command.publicId(),
                passwordHasher.encode(command.temporaryPassword())
        );
        int revokedSessions = userSessionPort.revokeActiveSessions(
                managedUser.internalId(),
                now
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetUserPublicId", updated.publicId());
        data.put("targetEmail", updated.email());
        data.put("revokedSessions", revokedSessions);

        auditLogPort.record(
                command.actorUserId(),
                "USER_PASSWORD_RESET",
                "USER_MANAGEMENT",
                "Se restableció la contraseña temporal de un usuario.",
                command.ipAddress(),
                command.userAgent(),
                data,
                now
        );
        return updated;
    }
}

package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuspendUserService {

    private final UserManagementPort userManagementPort;
    private final UserSessionPort userSessionPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public SuspendUserService(
            UserManagementPort userManagementPort,
            UserSessionPort userSessionPort,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.userManagementPort = userManagementPort;
        this.userSessionPort = userSessionPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary suspend(UserStatusCommand command) {
        UserManagementPort.ManagedUser managedUser =
                userManagementPort.getByPublicId(command.publicId());

        if (managedUser.internalId().equals(command.actorUserId())) {
            throw new BusinessException(
                    "SELF_SUSPEND_NOT_ALLOWED",
                    "No puedes suspender tu propia cuenta."
            );
        }

        Instant now = clock.instant();
        AdminUserSummary updated = userManagementPort.updateStatus(
                command.publicId(),
                UserStatus.SUSPENDED,
                UserAccessStatus.SUSPENDED,
                now
        );
        int revokedSessions = userSessionPort.revokeActiveSessions(
                managedUser.internalId(),
                now
        );

        var data = UserManagementSupport.auditData(
                managedUser.summary(),
                updated
        );
        data.put("revokedSessions", revokedSessions);

        auditLogPort.record(
                command.actorUserId(),
                "USER_SUSPENDED",
                "USER_MANAGEMENT",
                "Se suspendió un usuario y se invalidaron sus sesiones.",
                command.ipAddress(),
                command.userAgent(),
                data,
                now
        );
        return updated;
    }
}

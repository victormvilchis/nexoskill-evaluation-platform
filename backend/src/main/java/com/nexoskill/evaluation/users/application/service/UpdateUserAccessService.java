package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UpdateUserAccessCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUserAccessService {

    private final UserManagementPort userManagementPort;
    private final UserSessionPort userSessionPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public UpdateUserAccessService(
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
    public AdminUserSummary update(UpdateUserAccessCommand command) {
        UserManagementSupport.validateDates(command.startsAt(), command.expiresAt());
        UserManagementPort.ManagedUser managedUser =
                userManagementPort.getByPublicId(command.publicId());
        AdminUserSummary before = managedUser.summary();

        AdminUserSummary updated = userManagementPort.updateAccess(
                command.publicId(),
                command.startsAt(),
                command.expiresAt()
        );

        if (updated.accessStatus() != UserAccessStatus.ACTIVE) {
            userSessionPort.revokeActiveSessions(
                    managedUser.internalId(),
                    clock.instant()
            );
        }

        auditLogPort.record(
                command.actorUserId(),
                "USER_ACCESS_UPDATED",
                "USER_MANAGEMENT",
                "Se actualizó la vigencia de acceso de un usuario.",
                command.ipAddress(),
                command.userAgent(),
                UserManagementSupport.auditData(before, updated),
                clock.instant()
        );
        return updated;
    }
}

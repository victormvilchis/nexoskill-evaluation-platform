package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UpdateUserRoleCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUserRoleService {

    private final UserManagementPort userManagementPort;
    private final UserSessionPort userSessionPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public UpdateUserRoleService(
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
    public AdminUserSummary update(UpdateUserRoleCommand command) {
        UserManagementPort.ManagedUser managedUser =
                userManagementPort.getByPublicId(command.publicId());
        AdminUserSummary before = managedUser.summary();
        Instant now = clock.instant();

        AdminUserSummary updated = userManagementPort.updateRole(
                command.publicId(),
                command.roleCode().trim().toUpperCase()
        );
        int revokedSessions = userSessionPort.revokeActiveSessions(
                managedUser.internalId(),
                now
        );

        var data = UserManagementSupport.auditData(before, updated);
        data.put("revokedSessions", revokedSessions);

        auditLogPort.record(
                command.actorUserId(),
                "USER_ROLE_UPDATED",
                "USER_MANAGEMENT",
                "Se modificó el rol asignado y se invalidaron las sesiones del usuario.",
                command.ipAddress(),
                command.userAgent(),
                data,
                now
        );
        return updated;
    }
}

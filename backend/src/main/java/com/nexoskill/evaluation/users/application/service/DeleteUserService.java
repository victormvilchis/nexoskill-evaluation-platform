package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.DeleteUserCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteUserService {
    private final UserManagementPort users;
    private final UserSessionPort sessions;
    private final AuditLogPort audit;
    private final Clock clock;

    public DeleteUserService(UserManagementPort users, UserSessionPort sessions, AuditLogPort audit, Clock clock) {
        this.users = users; this.sessions = sessions; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public AdminUserSummary delete(DeleteUserCommand command) {
        var managed = users.getByPublicId(command.publicId());
        if (managed.internalId().equals(command.actorUserId())) {
            throw new BusinessException("SELF_DELETE_NOT_ALLOWED", "No puedes eliminar tu propia cuenta.");
        }
        Instant now = clock.instant();
        if (managed.summary().roles().contains("ADMINISTRATOR") && users.countEffectiveAdministrators(now) <= 1) {
            throw new BusinessException("LAST_ADMINISTRATOR_REQUIRED",
                    "No puedes eliminar al último administrador activo de la plataforma.");
        }
        if (managed.summary().status().name().equals("DELETED")) {
            throw new BusinessException("USER_ALREADY_DELETED", "El usuario ya se encuentra eliminado.");
        }
        AdminUserSummary updated = users.softDelete(command.publicId(), command.actorUserId(),
                normalizeReason(command.reason()), now);
        int revoked = sessions.revokeActiveSessions(managed.internalId(), now);
        var data = UserManagementSupport.auditData(managed.summary(), updated);
        data.put("revokedSessions", revoked);
        audit.record(command.actorUserId(), "USER_DELETED", "USER_MANAGEMENT",
                "Se eliminó lógicamente un usuario.", command.ipAddress(), command.userAgent(), data, now);
        return updated;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) return "Eliminación administrativa";
        String result = value.trim().replaceAll("\\s+", " ");
        return result.length() <= 500 ? result : result.substring(0, 500);
    }
}

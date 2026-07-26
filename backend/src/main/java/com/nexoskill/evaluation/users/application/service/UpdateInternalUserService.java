package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UpdateInternalUserCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateInternalUserService {
    private final UserManagementPort users;
    private final UserSessionPort sessions;
    private final InternalRolePolicy rolePolicy;
    private final AuditLogPort audit;
    private final Clock clock;

    public UpdateInternalUserService(UserManagementPort users, UserSessionPort sessions,
            InternalRolePolicy rolePolicy, AuditLogPort audit, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.rolePolicy = rolePolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary update(UpdateInternalUserCommand command) {
        UserManagementSupport.validateDates(command.startsAt(), command.expiresAt());
        var managed = users.getByPublicId(command.publicId());
        AdminUserSummary before = managed.summary();
        String normalizedEmail = EmailNormalizer.normalize(command.email());
        if (users.existsByNormalizedEmailExcluding(normalizedEmail, command.publicId())) {
            throw new BusinessException("USER_EMAIL_EXISTS", "Ya existe un usuario registrado con ese correo.");
        }

        String targetRole = rolePolicy.normalizeAndValidate(command.roleCode());
        rolePolicy.validateOrganization(targetRole, command.organizationPublicId());
        boolean self = managed.internalId().equals(command.actorUserId());
        String currentRole = before.roles().stream().findFirst().orElse("");
        if (self && !currentRole.equals(targetRole)) {
            throw new BusinessException("SELF_ROLE_CHANGE_NOT_ALLOWED", "No puedes modificar tu propio rol.");
        }
        if (self && !Objects.equals(before.organizationPublicId(), normalizeOrganization(command.organizationPublicId()))) {
            throw new BusinessException("SELF_ORGANIZATION_CHANGE_NOT_ALLOWED",
                    "No puedes modificar tu propia organización.");
        }

        Instant now = clock.instant();
        if (before.status() == com.nexoskill.evaluation.users.domain.model.UserStatus.ACTIVE
                && before.roles().contains("ADMINISTRATOR") && !"ADMINISTRATOR".equals(targetRole)
                && users.countEffectiveAdministrators(now) <= 1) {
            throw new BusinessException("LAST_ADMINISTRATOR_REQUIRED",
                    "No puedes quitar el rol al último administrador activo de la plataforma.");
        }

        AdminUserSummary updated = users.updateProfile(command.publicId(), command.email().trim(), normalizedEmail,
                command.firstName().trim(), command.lastName().trim(),
                UserManagementSupport.displayName(command.firstName(), command.lastName(), command.displayName()));
        updated = users.updateAccess(command.publicId(), command.startsAt(), command.expiresAt());
        updated = users.updateRole(command.publicId(), targetRole);
        updated = users.updateOrganization(command.publicId(), normalizeOrganization(command.organizationPublicId()),
                command.actorUserId(), now);

        boolean securityContextChanged = !before.roles().equals(updated.roles())
                || !Objects.equals(before.organizationPublicId(), updated.organizationPublicId())
                || !Objects.equals(before.startsAt(), updated.startsAt())
                || !Objects.equals(before.expiresAt(), updated.expiresAt());
        int revokedSessions = securityContextChanged ? sessions.revokeActiveSessions(managed.internalId(), now) : 0;

        var data = UserManagementSupport.auditData(before, updated);
        data.put("revokedSessions", revokedSessions);
        audit.record(command.actorUserId(), "USER_UPDATED", "USER_MANAGEMENT",
                "Se actualizaron los datos operativos de un usuario interno mediante una sola operación.",
                command.ipAddress(), command.userAgent(), data, now);
        return updated;
    }

    private String normalizeOrganization(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

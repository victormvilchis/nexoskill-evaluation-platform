package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.service.EmailNormalizer;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UpdateUserProfileCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUserProfileService {

    private final UserManagementPort userManagementPort;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public UpdateUserProfileService(
            UserManagementPort userManagementPort,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.userManagementPort = userManagementPort;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @Transactional
    public AdminUserSummary update(UpdateUserProfileCommand command) {
        AdminUserSummary before = userManagementPort
                .getByPublicId(command.publicId())
                .summary();
        String normalizedEmail = EmailNormalizer.normalize(command.email());

        if (userManagementPort.existsByNormalizedEmailExcluding(
                normalizedEmail,
                command.publicId())) {
            throw new BusinessException(
                    "USER_EMAIL_EXISTS",
                    "Ya existe otro usuario registrado con ese correo."
            );
        }

        AdminUserSummary updated = userManagementPort.updateProfile(
                command.publicId(),
                command.email().trim(),
                normalizedEmail,
                command.firstName().trim(),
                command.lastName().trim(),
                UserManagementSupport.displayName(
                        command.firstName(),
                        command.lastName(),
                        command.displayName()
                )
        );

        auditLogPort.record(
                command.actorUserId(),
                "USER_UPDATED",
                "USER_MANAGEMENT",
                "Se actualizaron los datos generales de un usuario.",
                command.ipAddress(),
                command.userAgent(),
                UserManagementSupport.auditData(before, updated),
                clock.instant()
        );
        return updated;
    }
}
